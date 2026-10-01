package cl.duoc.eventomax.catalog.service;

import cl.duoc.eventomax.catalog.controller.ResourceNotFoundException;
import cl.duoc.eventomax.catalog.domain.Equipment;
import cl.duoc.eventomax.catalog.domain.Inventory;
import cl.duoc.eventomax.catalog.domain.ProductionReservation;
import cl.duoc.eventomax.catalog.dto.ReservationItemRequest;
import cl.duoc.eventomax.catalog.dto.ReservationRequest;
import cl.duoc.eventomax.catalog.dto.ReservationResponse;
import cl.duoc.eventomax.catalog.exception.DuplicateEquipmentRequestException;
import cl.duoc.eventomax.catalog.exception.InactiveEquipmentException;
import cl.duoc.eventomax.catalog.exception.InsufficientInventoryException;
import cl.duoc.eventomax.catalog.exception.ReservationConflictException;
import cl.duoc.eventomax.catalog.repository.InventoryRepository;
import cl.duoc.eventomax.catalog.repository.ProductionReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
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

        when(inventoryRepository.findByEquipmentIdWithPessimisticWrite(1L)).thenReturn(Optional.of(inventory));
        when(reservationRepository.existsByProductionIdAndEquipmentId(123L, 1L)).thenReturn(false);
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
    void shouldThrowWhenInsufficientStock() {
        ReservationRequest request = new ReservationRequest(123L, List.of(new ReservationItemRequest(1L, 15)));

        when(inventoryRepository.findByEquipmentIdWithPessimisticWrite(1L)).thenReturn(Optional.of(inventory));
        when(reservationRepository.existsByProductionIdAndEquipmentId(123L, 1L)).thenReturn(false);

        assertThrows(InsufficientInventoryException.class, () -> service.reserve(request));

        verify(inventoryRepository, never()).save(any());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenEquipmentNotFound() {
        ReservationRequest request = new ReservationRequest(123L, List.of(new ReservationItemRequest(1L, 5)));

        when(inventoryRepository.findByEquipmentIdWithPessimisticWrite(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.reserve(request));
    }

    @Test
    void shouldThrowWhenEquipmentInactive() {
        equipment.setActive(false);
        ReservationRequest request = new ReservationRequest(123L, List.of(new ReservationItemRequest(1L, 5)));

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
    void shouldThrowWhenReservationConflict() {
        ReservationRequest request = new ReservationRequest(123L, List.of(new ReservationItemRequest(1L, 5)));

        when(inventoryRepository.findByEquipmentIdWithPessimisticWrite(1L)).thenReturn(Optional.of(inventory));
        when(reservationRepository.existsByProductionIdAndEquipmentId(123L, 1L)).thenReturn(true);

        assertThrows(ReservationConflictException.class, () -> service.reserve(request));
    }

    @Test
    void shouldNotLeavePartialChangesIfOneItemFails() throws Exception {
        Equipment eq2 = new Equipment("Luz", "Led", true);
        Field idField = Equipment.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(eq2, 2L);
        Inventory inv2 = new Inventory(eq2, 2);

        ReservationRequest request = new ReservationRequest(123L, List.of(
                new ReservationItemRequest(1L, 5),
                new ReservationItemRequest(2L, 5) // stock insuficiente
        ));

        when(inventoryRepository.findByEquipmentIdWithPessimisticWrite(1L)).thenReturn(Optional.of(inventory));
        when(reservationRepository.existsByProductionIdAndEquipmentId(123L, 1L)).thenReturn(false);
        when(inventoryRepository.findByEquipmentIdWithPessimisticWrite(2L)).thenReturn(Optional.of(inv2));
        when(reservationRepository.existsByProductionIdAndEquipmentId(123L, 2L)).thenReturn(false);

        assertThrows(InsufficientInventoryException.class, () -> service.reserve(request));

        verify(inventoryRepository, never()).save(any());
        verify(reservationRepository, never()).save(any());
        assertEquals(10, inventory.getAvailableQty()); // No se descontÃ³
        assertEquals(2, inv2.getAvailableQty()); // No se descontÃ³
    }
}
