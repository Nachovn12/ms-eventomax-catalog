package cl.duoc.eventomax.catalog.service;

import cl.duoc.eventomax.catalog.controller.ResourceNotFoundException;
import cl.duoc.eventomax.catalog.domain.Equipment;
import cl.duoc.eventomax.catalog.domain.Inventory;
import cl.duoc.eventomax.catalog.dto.EquipmentRequest;
import cl.duoc.eventomax.catalog.dto.EquipmentResponse;
import cl.duoc.eventomax.catalog.dto.EquipmentUpdateRequest;
import cl.duoc.eventomax.catalog.repository.EquipmentRepository;
import cl.duoc.eventomax.catalog.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EquipmentServiceTest {

    @Mock
    private EquipmentRepository equipmentRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @InjectMocks
    private EquipmentService equipmentService;

    private Equipment testEquipment;
    private Inventory testInventory;

    @BeforeEach
    void setUp() {
        testEquipment = new Equipment("Consola de Sonido", "32 canales", true);
        testInventory = new Inventory(testEquipment, 10);
    }

    @Test
    void shouldCreateEquipmentAndInitialInventory() {
        when(equipmentRepository.save(any(Equipment.class))).thenReturn(testEquipment);
        when(inventoryRepository.save(any(Inventory.class))).thenReturn(testInventory);

        EquipmentRequest request = new EquipmentRequest("Consola de Sonido", "32 canales", true, 10);
        EquipmentResponse response = equipmentService.createEquipment(request);

        assertNotNull(response);
        assertEquals("Consola de Sonido", response.name());
        assertEquals(10, response.availableQty());
        assertEquals(0, response.reservedQty());
        assertTrue(response.active());
    }

    @Test
    void shouldListAllEquipmentWithInventory() {
        when(equipmentRepository.findAll()).thenReturn(List.of(testEquipment));
        when(inventoryRepository.findByEquipmentIdWithoutLock(testEquipment.getId()))
                .thenReturn(Optional.of(testInventory));

        List<EquipmentResponse> result = equipmentService.listEquipment();

        assertEquals(1, result.size());
        assertEquals("Consola de Sonido", result.get(0).name());
        assertEquals(10, result.get(0).availableQty());
    }

    @Test
    void shouldGetEquipmentById() {
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(testEquipment));
        when(inventoryRepository.findByEquipmentIdWithoutLock(1L)).thenReturn(Optional.of(testInventory));

        EquipmentResponse response = equipmentService.getEquipmentById(1L);

        assertNotNull(response);
        assertEquals("Consola de Sonido", response.name());
    }

    @Test
    void shouldThrowNotFoundWhenEquipmentDoesNotExist() {
        when(equipmentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> equipmentService.getEquipmentById(999L));
    }

    @Test
    void shouldUpdateEquipmentAndAvailableStockPreservingReservedStock() {
        testInventory.setReservedQty(3); // Simular 3 unidades reservadas activas
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(testEquipment));
        when(inventoryRepository.findByEquipmentIdWithPessimisticWrite(1L)).thenReturn(Optional.of(testInventory));
        when(equipmentRepository.save(any(Equipment.class))).thenReturn(testEquipment);
        when(inventoryRepository.save(any(Inventory.class))).thenReturn(testInventory);

        EquipmentUpdateRequest request = new EquipmentUpdateRequest(
                "Consola de Sonido Pro", "32 canales mejorada", true, 25);

        EquipmentResponse response = equipmentService.updateEquipment(1L, request);

        assertNotNull(response);
        assertEquals("Consola de Sonido Pro", response.name());
        assertEquals("32 canales mejorada", response.description());
        assertEquals(25, response.availableQty());
        assertEquals(3, response.reservedQty()); // reservedQty preservado intacto
        assertTrue(response.active());

        verify(inventoryRepository).findByEquipmentIdWithPessimisticWrite(1L);
        verify(inventoryRepository).save(testInventory);
    }

    @Test
    void shouldUpdateEquipmentDetailsWithoutChangingStockWhenAvailableQtyIsNull() {
        testInventory.setReservedQty(5);
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(testEquipment));
        when(inventoryRepository.findByEquipmentIdWithPessimisticWrite(1L)).thenReturn(Optional.of(testInventory));
        when(equipmentRepository.save(any(Equipment.class))).thenReturn(testEquipment);

        EquipmentUpdateRequest request = new EquipmentUpdateRequest(
                "Consola Desactivada", "En mantenimiento", false, null);

        EquipmentResponse response = equipmentService.updateEquipment(1L, request);

        assertNotNull(response);
        assertEquals("Consola Desactivada", response.name());
        assertFalse(response.active());
        assertEquals(10, response.availableQty()); // Mantiene 10
        assertEquals(5, response.reservedQty());  // Mantiene 5

        verify(inventoryRepository, never()).save(any(Inventory.class));
    }

    @Test
    void shouldThrowNotFoundWhenUpdatingNonExistentEquipment() {
        when(equipmentRepository.findById(999L)).thenReturn(Optional.empty());

        EquipmentUpdateRequest request = new EquipmentUpdateRequest("Nombre", "Desc", true, 10);
        assertThrows(ResourceNotFoundException.class, () -> equipmentService.updateEquipment(999L, request));
    }
}

