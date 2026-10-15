package cl.duoc.eventomax.catalog.integration;

import cl.duoc.eventomax.catalog.domain.Equipment;
import cl.duoc.eventomax.catalog.domain.Inventory;
import cl.duoc.eventomax.catalog.dto.ReservationItemRequest;
import cl.duoc.eventomax.catalog.dto.ReservationItemResponse;
import cl.duoc.eventomax.catalog.dto.ReservationRequest;
import cl.duoc.eventomax.catalog.dto.ReservationResponse;
import cl.duoc.eventomax.catalog.exception.InsufficientInventoryException;
import cl.duoc.eventomax.catalog.exception.ReservationConflictException;
import cl.duoc.eventomax.catalog.repository.EquipmentRepository;
import cl.duoc.eventomax.catalog.repository.InventoryRepository;
import cl.duoc.eventomax.catalog.repository.ProductionReservationRepository;
import cl.duoc.eventomax.catalog.service.InventoryReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
public class InventoryConcurrencyIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("eventomax_catalog_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private InventoryReservationService reservationService;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ProductionReservationRepository reservationRepository;

    private Long testEquipmentId;

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAll();
        inventoryRepository.deleteAll();
        equipmentRepository.deleteAll();

        Equipment eq = equipmentRepository.save(new Equipment("Foco Robótico 500W", "Foco móvil DMX para conciertos", true));
        Inventory inv = inventoryRepository.save(new Inventory(eq, 10)); // 10 unidades disponibles
        testEquipmentId = eq.getId();
    }

    @Test
    void testConcurrentReservationsCompetingForStockDoNotOversell() throws Exception {
        // Dos producciones distintas compiten por 6 unidades cada una (total solicitado = 12, disponible = 10)
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final long productionId = 100L + i;
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    ReservationRequest req = new ReservationRequest(productionId, List.of(new ReservationItemRequest(testEquipmentId, 6)));
                    reservationService.reserve(req);
                    successCount.incrementAndGet();
                } catch (InsufficientInventoryException e) {
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    fail("Unexpected exception: " + e.getMessage());
                }
            }));
        }

        // Desencadenar ambos hilos al mismo tiempo
        latch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));

        assertEquals(1, successCount.get(), "Exactamente una confirmación debe tener éxito");
        assertEquals(1, conflictCount.get(), "Exactamente una confirmación debe fallar por falta de stock");

        Inventory finalInventory = inventoryRepository.findByEquipmentIdWithoutLock(testEquipmentId).orElseThrow();
        assertEquals(4, finalInventory.getAvailableQty(), "Stock restante debe ser exactamente 10 - 6 = 4");
        assertEquals(6, finalInventory.getReservedQty(), "Stock reservado debe ser exactamente 6");
    }

    @Test
    void testIdempotentReplayDoesNotDeductAdditionalStock() {
        long productionId = 200L;
        ReservationRequest req = new ReservationRequest(productionId, List.of(new ReservationItemRequest(testEquipmentId, 4)));

        // Primera llamada
        ReservationResponse res1 = reservationService.reserve(req);
        assertEquals(productionId, res1.productionId());
        assertEquals(4, res1.items().get(0).quantity());

        Inventory afterFirst = inventoryRepository.findByEquipmentIdWithoutLock(testEquipmentId).orElseThrow();
        assertEquals(6, afterFirst.getAvailableQty());
        assertEquals(4, afterFirst.getReservedQty());

        // Segunda llamada idéntica
        ReservationResponse res2 = reservationService.reserve(req);
        assertEquals(productionId, res2.productionId());
        assertEquals(4, res2.items().get(0).quantity());

        Inventory afterSecond = inventoryRepository.findByEquipmentIdWithoutLock(testEquipmentId).orElseThrow();
        assertEquals(6, afterSecond.getAvailableQty(), "El stock disponible no debe descontarse de nuevo");
        assertEquals(4, afterSecond.getReservedQty(), "El stock reservado no debe incrementarse de nuevo");
    }

    @Test
    void testIncompatibleRetryThrowsReservationConflict() {
        long productionId = 300L;
        ReservationRequest req1 = new ReservationRequest(productionId, List.of(new ReservationItemRequest(testEquipmentId, 3)));
        reservationService.reserve(req1);

        // Reintento con cantidad incompatible (5 en lugar de 3)
        ReservationRequest reqIncompatible = new ReservationRequest(productionId, List.of(new ReservationItemRequest(testEquipmentId, 5)));

        assertThrows(ReservationConflictException.class, () -> reservationService.reserve(reqIncompatible));

        // El stock permanece intacto en 10 - 3 = 7
        Inventory current = inventoryRepository.findByEquipmentIdWithoutLock(testEquipmentId).orElseThrow();
        assertEquals(7, current.getAvailableQty());
        assertEquals(3, current.getReservedQty());
    }

    @Test
    void testTechnicalCompensationRestoresStock() {
        long productionId = 400L;
        ReservationRequest req = new ReservationRequest(productionId, List.of(new ReservationItemRequest(testEquipmentId, 4)));
        reservationService.reserve(req);

        Inventory beforeComp = inventoryRepository.findByEquipmentIdWithoutLock(testEquipmentId).orElseThrow();
        assertEquals(6, beforeComp.getAvailableQty());
        assertEquals(4, beforeComp.getReservedQty());

        // Compensación técnica
        List<ReservationItemResponse> compensated = reservationService.compensate(productionId);
        assertEquals(1, compensated.size());
        assertEquals("COMPENSATED", compensated.get(0).status());

        Inventory afterComp = inventoryRepository.findByEquipmentIdWithoutLock(testEquipmentId).orElseThrow();
        assertEquals(10, afterComp.getAvailableQty(), "Stock disponible restituido a 10");
        assertEquals(0, afterComp.getReservedQty(), "Stock reservado restituido a 0");

        // Compensación repetida (idempotente) no suma stock extra
        List<ReservationItemResponse> secondComp = reservationService.compensate(productionId);
        assertTrue(secondComp.isEmpty(), "No debe haber reservas activas para compensar de nuevo");
        Inventory afterSecondComp = inventoryRepository.findByEquipmentIdWithoutLock(testEquipmentId).orElseThrow();
        assertEquals(10, afterSecondComp.getAvailableQty());
        assertEquals(0, afterSecondComp.getReservedQty());
    }

    @Test
    void testQueryReservationsByProduction() {
        long productionId = 500L;
        ReservationRequest req = new ReservationRequest(productionId, List.of(new ReservationItemRequest(testEquipmentId, 2)));
        reservationService.reserve(req);

        List<ReservationItemResponse> list = reservationService.getReservationsByProduction(productionId);
        assertEquals(1, list.size());
        assertEquals(testEquipmentId, list.get(0).equipmentId());
        assertEquals("Foco Robótico 500W", list.get(0).equipmentName());
        assertEquals(2, list.get(0).quantity());
        assertEquals("RESERVED", list.get(0).status());
    }
}
