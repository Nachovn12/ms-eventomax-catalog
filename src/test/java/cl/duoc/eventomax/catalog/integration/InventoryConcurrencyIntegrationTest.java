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
import org.junit.jupiter.api.Disabled;
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

    @Test
    void testConcurrentCompensationsDoNotInflateStock() throws Exception {
        long productionId = 600L;
        ReservationRequest req = new ReservationRequest(productionId, List.of(new ReservationItemRequest(testEquipmentId, 4)));
        reservationService.reserve(req);

        Inventory before = inventoryRepository.findByEquipmentIdWithoutLock(testEquipmentId).orElseThrow();
        assertEquals(6, before.getAvailableQty());
        assertEquals(4, before.getReservedQty());

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    reservationService.compensate(productionId);
                } catch (Exception e) {
                    // ignore
                }
            }));
        }

        latch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));

        Inventory after = inventoryRepository.findByEquipmentIdWithoutLock(testEquipmentId).orElseThrow();
        assertEquals(10, after.getAvailableQty(), "El stock disponible restaurado debe ser exactamente 10, sin inflacion por compensacion concurrente");
        assertEquals(0, after.getReservedQty(), "El stock reservado debe ser exactamente 0");

        // Repetir la compensación: stock permanece en 10 y reservado en 0
        List<ReservationItemResponse> repeatedComp = reservationService.compensate(productionId);
        assertTrue(repeatedComp.isEmpty(), "Compensación subsecuente no debe procesar reservas ya compensadas");
        Inventory afterRepeat = inventoryRepository.findByEquipmentIdWithoutLock(testEquipmentId).orElseThrow();
        assertEquals(10, afterRepeat.getAvailableQty(), "El stock debe permanecer en 10 tras repetición");
        assertEquals(0, afterRepeat.getReservedQty(), "El stock reservado debe permanecer en 0 tras repetición");
    }

    @Test
    void testConcurrentReservationsSameProductionIdDoNotCorruptState() throws Exception {
        long productionId = 700L;
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    ReservationRequest req = new ReservationRequest(productionId, List.of(new ReservationItemRequest(testEquipmentId, 3)));
                    ReservationResponse resp = reservationService.reserve(req);
                    assertNotNull(resp);
                    assertEquals(productionId, resp.productionId());
                    assertEquals(1, resp.items().size());
                    assertEquals(3, resp.items().get(0).quantity());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                }
            }));
        }

        latch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));

        assertEquals(2, successCount.get(), "Ambas peticiones idénticas deben completarse con éxito (una crea y la otra devuelve repetición idempotente)");
        assertEquals(0, errorCount.get(), "Ninguna petición debe fallar por error 500 o violación de unicidad");

        Inventory after = inventoryRepository.findByEquipmentIdWithoutLock(testEquipmentId).orElseThrow();
        // If state is not corrupted, stock available must be 7 (10 - 3), NOT 4 (10 - 6)!
        assertEquals(7, after.getAvailableQty(), "El stock disponible debe haberse descontado exactamente una vez (7 unidades)");
        assertEquals(3, after.getReservedQty(), "El stock reservado debe ser exactamente 3");
    }

    @Test
    void testConcurrentIncompatibleReservationsSameProductionControlledConflict() throws Exception {
        long productionId = 800L;
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger unexpectedErrorCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();

        // Hilo 1 solicita 3 unidades, Hilo 2 solicita 5 unidades para la MISMA producción
        futures.add(executor.submit(() -> {
            try {
                latch.await();
                ReservationRequest req = new ReservationRequest(productionId, List.of(new ReservationItemRequest(testEquipmentId, 3)));
                reservationService.reserve(req);
                successCount.incrementAndGet();
            } catch (ReservationConflictException e) {
                conflictCount.incrementAndGet();
            } catch (Exception e) {
                unexpectedErrorCount.incrementAndGet();
            }
        }));

        futures.add(executor.submit(() -> {
            try {
                latch.await();
                ReservationRequest req = new ReservationRequest(productionId, List.of(new ReservationItemRequest(testEquipmentId, 5)));
                reservationService.reserve(req);
                successCount.incrementAndGet();
            } catch (ReservationConflictException e) {
                conflictCount.incrementAndGet();
            } catch (Exception e) {
                unexpectedErrorCount.incrementAndGet();
            }
        }));

        latch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));

        assertEquals(1, successCount.get(), "Exactamente una de las dos solicitudes incompatibles debe ganar la reserva");
        assertEquals(1, conflictCount.get(), "La solicitud incompatible debe recibir rechazo controlado ReservationConflictException");
        assertEquals(0, unexpectedErrorCount.get(), "No debe haber errores 500 ni transacciones de PostgreSQL abortadas");
    }

    @Test
    void testMultiEquipmentConcurrentCompensations() throws Exception {
        Equipment eq2 = equipmentRepository.save(new Equipment("Mesa de Sonido 32ch", "Consola digital", true));
        inventoryRepository.save(new Inventory(eq2, 5)); // 5 disponibles

        long productionId = 900L;
        // Reservar 4 de foco y 2 de consola
        ReservationRequest req = new ReservationRequest(productionId, List.of(
                new ReservationItemRequest(testEquipmentId, 4),
                new ReservationItemRequest(eq2.getId(), 2)
        ));
        reservationService.reserve(req);

        Inventory focoBefore = inventoryRepository.findByEquipmentIdWithoutLock(testEquipmentId).orElseThrow();
        assertEquals(6, focoBefore.getAvailableQty());
        assertEquals(4, focoBefore.getReservedQty());

        Inventory consolaBefore = inventoryRepository.findByEquipmentIdWithoutLock(eq2.getId()).orElseThrow();
        assertEquals(3, consolaBefore.getAvailableQty());
        assertEquals(2, consolaBefore.getReservedQty());

        // Lanzar dos compensaciones concurrentes de ambos equipos
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    reservationService.compensate(productionId);
                } catch (Exception e) {
                    // ignore
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));

        Inventory focoAfter = inventoryRepository.findByEquipmentIdWithoutLock(testEquipmentId).orElseThrow();
        assertEquals(10, focoAfter.getAvailableQty(), "Foco debe restaurarse exactamente a 10");
        assertEquals(0, focoAfter.getReservedQty(), "Foco reservado debe ser 0");

        Inventory consolaAfter = inventoryRepository.findByEquipmentIdWithoutLock(eq2.getId()).orElseThrow();
        assertEquals(5, consolaAfter.getAvailableQty(), "Consola debe restaurarse exactamente a 5");
        assertEquals(0, consolaAfter.getReservedQty(), "Consola reservada debe ser 0");
    }

    @Test
    void testConcurrentReservationsSameProductionDisjointEquipmentSerializesAndPreventsConflict() throws Exception {
        Equipment eq2 = equipmentRepository.save(new Equipment("Micrófono Inalámbrico", "Shure SM58", true));
        inventoryRepository.save(new Inventory(eq2, 8)); // 8 disponibles

        long productionId = 950L;
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger unexpectedErrorCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();

        // Hilo 1 solicita 4 unidades de foco (testEquipmentId) para productionId 950
        futures.add(executor.submit(() -> {
            try {
                latch.await();
                ReservationRequest req = new ReservationRequest(productionId, List.of(new ReservationItemRequest(testEquipmentId, 4)));
                reservationService.reserve(req);
                successCount.incrementAndGet();
            } catch (ReservationConflictException e) {
                conflictCount.incrementAndGet();
            } catch (Exception e) {
                unexpectedErrorCount.incrementAndGet();
            }
        }));

        // Hilo 2 solicita 3 unidades de micrófono (eq2) para la MISMA productionId 950 (equipamiento disjunto)
        futures.add(executor.submit(() -> {
            try {
                latch.await();
                ReservationRequest req = new ReservationRequest(productionId, List.of(new ReservationItemRequest(eq2.getId(), 3)));
                reservationService.reserve(req);
                successCount.incrementAndGet();
            } catch (ReservationConflictException e) {
                conflictCount.incrementAndGet();
            } catch (Exception e) {
                unexpectedErrorCount.incrementAndGet();
            }
        }));

        latch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));

        assertEquals(1, successCount.get(), "Exactamente una de las dos solicitudes disjuntas debe ganar la reserva de la producción");
        assertEquals(1, conflictCount.get(), "La solicitud concurrente debe ser rechazada con ReservationConflictException gracias al advisory lock");
        assertEquals(0, unexpectedErrorCount.get(), "No debe haber errores inesperados");

        // Verificar consistencia de inventario: solo la reserva ganadora debe haber descontado stock
        List<cl.duoc.eventomax.catalog.domain.ProductionReservation> active = reservationRepository.findByProductionId(productionId);
        assertEquals(1, active.size(), "Solo debe haber una reserva activa para la producción, sin mezclas disjuntas");

        if (active.get(0).getEquipment().getId().equals(testEquipmentId)) {
            // Ganó foco: foco descuenta 4, micrófono queda intacto en 8 disponibles y 0 reservados
            Inventory foco = inventoryRepository.findByEquipmentIdWithoutLock(testEquipmentId).orElseThrow();
            assertEquals(6, foco.getAvailableQty());
            assertEquals(4, foco.getReservedQty());

            Inventory mic = inventoryRepository.findByEquipmentIdWithoutLock(eq2.getId()).orElseThrow();
            assertEquals(8, mic.getAvailableQty());
            assertEquals(0, mic.getReservedQty());
        } else {
            // Ganó micrófono: micrófono descuenta 3, foco queda intacto en 10 disponibles y 0 reservados
            Inventory mic = inventoryRepository.findByEquipmentIdWithoutLock(eq2.getId()).orElseThrow();
            assertEquals(5, mic.getAvailableQty());
            assertEquals(3, mic.getReservedQty());

            Inventory foco = inventoryRepository.findByEquipmentIdWithoutLock(testEquipmentId).orElseThrow();
            assertEquals(10, foco.getAvailableQty());
            assertEquals(0, foco.getReservedQty());
        }
    }

    @Test
    void compensationBeforeReserveClosesGenerationDurably() {
        long id = 90001L;
        var request = new ReservationRequest(id, List.of(new ReservationItemRequest(testEquipmentId, 2)));
        reservationService.compensate(id, 1L);
        assertThrows(ReservationConflictException.class, () -> reservationService.reserve(request, 1L));
        var inventory = inventoryRepository.findByEquipmentIdWithoutLock(testEquipmentId).orElseThrow();
        assertEquals(10, inventory.getAvailableQty());
        assertEquals(0, inventory.getReservedQty());
    }

    @Test
    void staleCompensationCannotReleaseNewGenerationAndLegacyCannotBypassFence() {
        long id = 90002L;
        var request = new ReservationRequest(id, List.of(new ReservationItemRequest(testEquipmentId, 2)));
        reservationService.reserve(request, 1L);
        reservationService.compensate(id, 1L);
        reservationService.reserve(request, 2L);
        reservationService.compensate(id, 1L);
        assertThrows(ReservationConflictException.class, () -> reservationService.reserve(request, 1L));
        assertThrows(ReservationConflictException.class, () -> reservationService.compensate(id));
        assertThrows(ReservationConflictException.class, () -> reservationService.reserve(request));
        var inventory = inventoryRepository.findByEquipmentIdWithoutLock(testEquipmentId).orElseThrow();
        assertEquals(8, inventory.getAvailableQty());
        assertEquals(2, inventory.getReservedQty());
        reservationService.compensate(id, 2L);
        reservationService.compensate(id, 2L);
        assertEquals(10, inventoryRepository.findByEquipmentIdWithoutLock(testEquipmentId).orElseThrow().getAvailableQty());
    }

    @Test
    void futureGenerationCompensationCannotReleaseActivePreviousGeneration() {
        long id = 90003L;
        var request = new ReservationRequest(id, List.of(new ReservationItemRequest(testEquipmentId, 3)));
        reservationService.reserve(request, 1L);

        // An incoming compensation for future generation 2 must NOT release generation 1's active reservation
        assertThrows(ReservationConflictException.class, () -> reservationService.compensate(id, 2L));

        // Verify inventory of generation 1 is still safely reserved
        var inventory = inventoryRepository.findByEquipmentIdWithoutLock(testEquipmentId).orElseThrow();
        assertEquals(7, inventory.getAvailableQty());
        assertEquals(3, inventory.getReservedQty());

        // Legitimate compensation of generation 1 releases it
        reservationService.compensate(id, 1L);
        assertEquals(10, inventoryRepository.findByEquipmentIdWithoutLock(testEquipmentId).orElseThrow().getAvailableQty());
    }
}
