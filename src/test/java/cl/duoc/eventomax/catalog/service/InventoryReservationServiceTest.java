package cl.duoc.eventomax.catalog.service;

import cl.duoc.eventomax.catalog.controller.ResourceNotFoundException;
import cl.duoc.eventomax.catalog.domain.Equipment;
import cl.duoc.eventomax.catalog.domain.Inventory;
import cl.duoc.eventomax.catalog.domain.ProductionReservation;
import cl.duoc.eventomax.catalog.domain.ReservationStatus;
import cl.duoc.eventomax.catalog.dto.ReservationItemRequest;
import cl.duoc.eventomax.catalog.dto.ReservationItemResponse;
import cl.duoc.eventomax.catalog.dto.ReservationRequest;
import cl.duoc.eventomax.catalog.dto.ReservationResponse;
import cl.duoc.eventomax.catalog.exception.DuplicateEquipmentRequestException;
import cl.duoc.eventomax.catalog.exception.InactiveEquipmentException;
import cl.duoc.eventomax.catalog.exception.InsufficientInventoryException;
import cl.duoc.eventomax.catalog.exception.ReservationConflictException;
import cl.duoc.eventomax.catalog.repository.InventoryRepository;
import cl.duoc.eventomax.catalog.repository.ProductionReservationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryReservationServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private ProductionReservationRepository reservationRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private InventoryReservationService service;

    private Equipment equipment;
    private Inventory inventory;

    @BeforeEach
    void setUp() throws Exception {
        equipment = new Equipment("Camara", "4K", true);
        Field idField = Equipment.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(equipment, 1L);

        inventory = new Inventory(equipment, 10);
    }

    @Test
    void shouldReserveSuccessfully() {
        ReservationRequest request = new ReservationRequest(123L, List.of(new ReservationItemRequest(1L, 5)));

        when(reservationRepository.findByProductionId(123L)).thenReturn(Collections.emptyList());
        when(inventoryRepository.findByEquipmentIdWithPessimisticWrite(1L)).thenReturn(Optional.of(inventory));
        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReservationResponse response = service.reserve(request);

        assertEquals(123L, response.productionId());
        assertEquals(1, response.items().size());
        assertEquals(5, inventory.getAvailableQty());
        assertEquals(5, inventory.getReservedQty());
        verify(inventoryRepository).save(inventory);
        verify(reservationRepository).save(any(ProductionReservation.class));
    }

    @Test
    void shouldReplayIdempotentlyWhenSameItemsAlreadyReserved() {
        ReservationRequest request = new ReservationRequest(123L, List.of(new ReservationItemRequest(1L, 5)));

        ProductionReservation existing = new ProductionReservation(123L, equipment, 5);
        when(reservationRepository.findByProductionId(123L)).thenReturn(List.of(existing));

        ReservationResponse response = service.reserve(request);

        assertEquals(123L, response.productionId());
        assertEquals(1, response.items().size());
        assertEquals(5, response.items().get(0).quantity());
        // Verify no extra stock was touched
        verify(inventoryRepository, never()).findByEquipmentIdWithPessimisticWrite(any());
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void shouldThrowConflictWhenReusingIdentityWithIncompatibleItems() {
        // Request asking for 8 units, but previously reserved 5
        ReservationRequest request = new ReservationRequest(123L, List.of(new ReservationItemRequest(1L, 8)));

        ProductionReservation existing = new ProductionReservation(123L, equipment, 5);
        when(reservationRepository.findByProductionId(123L)).thenReturn(List.of(existing));

        assertThrows(ReservationConflictException.class, () -> service.reserve(request));
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenInsufficientStock() {
        ReservationRequest request = new ReservationRequest(123L, List.of(new ReservationItemRequest(1L, 15)));

        when(reservationRepository.findByProductionId(123L)).thenReturn(Collections.emptyList());
        when(inventoryRepository.findByEquipmentIdWithPessimisticWrite(1L)).thenReturn(Optional.of(inventory));

        assertThrows(InsufficientInventoryException.class, () -> service.reserve(request));

        verify(inventoryRepository, never()).save(any());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenEquipmentNotFound() {
        ReservationRequest request = new ReservationRequest(123L, List.of(new ReservationItemRequest(1L, 5)));

        when(reservationRepository.findByProductionId(123L)).thenReturn(Collections.emptyList());
        when(inventoryRepository.findByEquipmentIdWithPessimisticWrite(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.reserve(request));
    }

    @Test
    void shouldThrowWhenEquipmentInactive() {
        equipment.setActive(false);
        ReservationRequest request = new ReservationRequest(123L, List.of(new ReservationItemRequest(1L, 5)));

        when(reservationRepository.findByProductionId(123L)).thenReturn(Collections.emptyList());
        when(inventoryRepository.findByEquipmentIdWithPessimisticWrite(1L)).thenReturn(Optional.of(inventory));

        assertThrows(InactiveEquipmentException.class, () -> service.reserve(request));
    }

    @Test
    void shouldThrowWhenDuplicateEquipmentInRequest() {
        ReservationRequest request = new ReservationRequest(123L, List.of(
                new ReservationItemRequest(1L, 5),
                new ReservationItemRequest(1L, 2)
        ));

        assertThrows(DuplicateEquipmentRequestException.class, () -> service.reserve(request));
    }

    @Test
    void shouldCompensateSuccessfullyRestoringStock() throws Exception {
        ProductionReservation existing = new ProductionReservation(123L, equipment, 4);
        Field resIdField = ProductionReservation.class.getDeclaredField("id");
        resIdField.setAccessible(true);
        resIdField.set(existing, 1L);

        inventory.setAvailableQty(6);
        inventory.setReservedQty(4);

        when(reservationRepository.findByProductionIdAndStatus(123L, ReservationStatus.RESERVED))
                .thenReturn(List.of(existing));
        when(inventoryRepository.findByEquipmentIdWithPessimisticWrite(1L))
                .thenReturn(Optional.of(inventory));
        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<ReservationItemResponse> compensated = service.compensate(123L);

        assertEquals(1, compensated.size());
        assertEquals("COMPENSATED", compensated.get(0).status());
        assertEquals(10, inventory.getAvailableQty());
        assertEquals(0, inventory.getReservedQty());
        verify(inventoryRepository).save(inventory);
        verify(reservationRepository).save(existing);
        verify(entityManager).refresh(existing, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void shouldThrowWhenCompensatingWithNullId() {
        ProductionReservation existing = new ProductionReservation(123L, equipment, 4);
        // id is left as null
        when(reservationRepository.findByProductionIdAndStatus(123L, ReservationStatus.RESERVED))
                .thenReturn(List.of(existing));

        assertThrows(IllegalStateException.class, () -> service.compensate(123L));
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenCompensatingViolatesReservedQuantityInvariant() throws Exception {
        ProductionReservation existing = new ProductionReservation(123L, equipment, 4);
        Field resIdField = ProductionReservation.class.getDeclaredField("id");
        resIdField.setAccessible(true);
        resIdField.set(existing, 1L);

        inventory.setAvailableQty(6);
        inventory.setReservedQty(2); // Invariant violation: reservedQty (2) < compensation quantity (4)

        when(reservationRepository.findByProductionIdAndStatus(123L, ReservationStatus.RESERVED))
                .thenReturn(List.of(existing));
        when(inventoryRepository.findByEquipmentIdWithPessimisticWrite(1L))
                .thenReturn(Optional.of(inventory));

        assertThrows(IllegalStateException.class, () -> service.compensate(123L));
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void shouldQueryReservationsByProduction() {
        ProductionReservation res = new ProductionReservation(123L, equipment, 3);
        when(reservationRepository.findByProductionId(123L)).thenReturn(List.of(res));

        List<ReservationItemResponse> list = service.getReservationsByProduction(123L);
        assertEquals(1, list.size());
        assertEquals(3, list.get(0).quantity());
        assertEquals("Camara", list.get(0).equipmentName());
    }
}
