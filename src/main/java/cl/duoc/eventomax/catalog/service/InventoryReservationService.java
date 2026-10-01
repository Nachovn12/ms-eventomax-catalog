package cl.duoc.eventomax.catalog.service;

import cl.duoc.eventomax.catalog.controller.ResourceNotFoundException;
import cl.duoc.eventomax.catalog.domain.Equipment;
import cl.duoc.eventomax.catalog.domain.Inventory;
import cl.duoc.eventomax.catalog.domain.ProductionReservation;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class InventoryReservationService {

    private final InventoryRepository inventoryRepository;
    private final ProductionReservationRepository reservationRepository;

    public InventoryReservationService(InventoryRepository inventoryRepository, ProductionReservationRepository reservationRepository) {
        this.inventoryRepository = inventoryRepository;
        this.reservationRepository = reservationRepository;
    }

    @Transactional
    public ReservationResponse reserve(ReservationRequest request) {
        // A. Validar que no existan equipmentId duplicados en request.items
        Set<Long> uniqueEquipmentIds = new HashSet<>();
        for (ReservationItemRequest item : request.items()) {
            if (!uniqueEquipmentIds.add(item.equipmentId())) {
                throw new DuplicateEquipmentRequestException("Duplicate equipmentId " + item.equipmentId() + " in request");
            }
        }

        // B. Ordenar los items por equipmentId ascendente antes de adquirir locks
        List<ReservationItemRequest> sortedItems = new ArrayList<>(request.items());
        sortedItems.sort(Comparator.comparing(ReservationItemRequest::equipmentId));

        List<Inventory> inventoriesToUpdate = new ArrayList<>();
        List<ProductionReservation> reservationsToSave = new ArrayList<>();
        List<ReservationItemResponse> itemResponses = new ArrayList<>();

        // C. Adquirir locks y validar TODOS los items primero
        for (ReservationItemRequest item : sortedItems) {
            Inventory inventory = inventoryRepository.findByEquipmentIdWithPessimisticWrite(item.equipmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Equipment/Inventory not found for id: " + item.equipmentId()));

            Equipment equipment = inventory.getEquipment();

            if (!equipment.getActive()) {
                throw new InactiveEquipmentException("Equipment " + item.equipmentId() + " is inactive");
            }

            if (reservationRepository.existsByProductionIdAndEquipmentId(request.productionId(), item.equipmentId())) {
                throw new ReservationConflictException("Reservation already exists for productionId " + request.productionId() + " and equipmentId " + item.equipmentId());
            }

            if (inventory.getAvailableQty() < item.quantity()) {
                throw new InsufficientInventoryException("Insufficient stock for equipmentId " + item.equipmentId() + ". Available: " + inventory.getAvailableQty() + ", Requested: " + item.quantity());
            }

            inventoriesToUpdate.add(inventory);

            ProductionReservation reservation = new ProductionReservation(request.productionId(), equipment, item.quantity());
            reservationsToSave.add(reservation);
        }

        // F. Si todo es vÃƒÂ¡lido: actualizar y guardar
        for (int i = 0; i < sortedItems.size(); i++) {
            ReservationItemRequest itemRequest = sortedItems.get(i);
            Inventory inventory = inventoriesToUpdate.get(i);
            ProductionReservation reservation = reservationsToSave.get(i);

            inventory.setAvailableQty(inventory.getAvailableQty() - itemRequest.quantity());
            inventory.setReservedQty(inventory.getReservedQty() + itemRequest.quantity());

            inventoryRepository.save(inventory);
            reservation = reservationRepository.save(reservation);

            itemResponses.add(new ReservationItemResponse(reservation.getId(), reservation.getEquipment().getId(), reservation.getQuantity(), reservation.getStatus().name()));
        }

        return new ReservationResponse(request.productionId(), itemResponses);
    }
}
