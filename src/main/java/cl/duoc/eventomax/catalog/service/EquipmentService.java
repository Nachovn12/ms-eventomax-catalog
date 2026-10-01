package cl.duoc.eventomax.catalog.service;

import cl.duoc.eventomax.catalog.controller.ResourceNotFoundException;
import cl.duoc.eventomax.catalog.domain.Equipment;
import cl.duoc.eventomax.catalog.domain.Inventory;
import cl.duoc.eventomax.catalog.dto.EquipmentRequest;
import cl.duoc.eventomax.catalog.dto.EquipmentResponse;
import cl.duoc.eventomax.catalog.repository.EquipmentRepository;
import cl.duoc.eventomax.catalog.repository.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class EquipmentService {

    private final EquipmentRepository equipmentRepository;
    private final InventoryRepository inventoryRepository;

    public EquipmentService(EquipmentRepository equipmentRepository, InventoryRepository inventoryRepository) {
        this.equipmentRepository = equipmentRepository;
        this.inventoryRepository = inventoryRepository;
    }

    @Transactional
    public EquipmentResponse createEquipment(EquipmentRequest request) {
        Equipment equipment = new Equipment(request.name(), request.description(), request.active());
        equipment = equipmentRepository.save(equipment);

        Inventory inventory = new Inventory(equipment, request.initialAvailableQty());
        inventory = inventoryRepository.save(inventory);

        return mapToResponse(equipment, inventory);
    }

    @Transactional(readOnly = true)
    public List<EquipmentResponse> listEquipment() {
        return equipmentRepository.findAll().stream()
                .map(eq -> {
                    Inventory inv = inventoryRepository.findByEquipmentIdWithPessimisticWrite(eq.getId())
                            .orElse(new Inventory(eq, 0));
                    return mapToResponse(eq, inv);
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public EquipmentResponse getEquipmentById(Long id) {
        Equipment equipment = equipmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Equipment not found"));
        Inventory inventory = inventoryRepository.findByEquipmentIdWithPessimisticWrite(id)
                .orElse(new Inventory(equipment, 0));

        return mapToResponse(equipment, inventory);
    }

    private EquipmentResponse mapToResponse(Equipment equipment, Inventory inventory) {
        return new EquipmentResponse(
                equipment.getId(),
                equipment.getName(),
                equipment.getDescription(),
                equipment.getActive(),
                inventory.getAvailableQty(),
                inventory.getReservedQty()
        );
    }
}
