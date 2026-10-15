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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("Reservation request items cannot be null or empty");
        }

        // A. Validar que no existan equipmentId duplicados o inválidos en request.items
        Set<Long> uniqueEquipmentIds = new HashSet<>();
        for (ReservationItemRequest item : request.items()) {
            if (item.equipmentId() == null || item.equipmentId() < 1) {
                throw new IllegalArgumentException("equipmentId must be positive");
            }
            if (item.quantity() == null || item.quantity() < 1) {
                throw new IllegalArgumentException("quantity must be positive");
            }
            if (!uniqueEquipmentIds.add(item.equipmentId())) {
                throw new DuplicateEquipmentRequestException("Duplicate equipmentId " + item.equipmentId() + " in request");
            }
        }

        // B. Verificar reservas existentes para esta productionId
        List<ProductionReservation> existingReservations = reservationRepository.findByProductionId(request.productionId());
        List<ProductionReservation> activeReservations = existingReservations.stream()
                .filter(r -> r.getStatus() == ReservationStatus.RESERVED)
                .toList();

        if (!activeReservations.isEmpty()) {
            Map<Long, Integer> existingMap = activeReservations.stream()
                    .collect(Collectors.toMap(r -> r.getEquipment().getId(), ProductionReservation::getQuantity));
            Map<Long, Integer> requestedMap = request.items().stream()
                    .collect(Collectors.toMap(ReservationItemRequest::equipmentId, ReservationItemRequest::quantity));

            if (existingMap.equals(requestedMap)) {
                // Idempotent replay: contenido idéntico ya reservado, no descontar nuevamente
                List<ReservationItemResponse> replayResponses = activeReservations.stream()
                        .map(r -> new ReservationItemResponse(
                                r.getId(),
                                r.getEquipment().getId(),
                                r.getEquipment().getName(),
                                r.getQuantity(),
                                r.getStatus().name()
                        ))
                        .toList();
                return new ReservationResponse(request.productionId(), replayResponses);
            } else {
                // Contenido incompatible con la reserva activa existente
                throw new ReservationConflictException(
                        "Incompatible reservation already exists for productionId " + request.productionId()
                );
            }
        }

        // C. Ordenar los items por equipmentId ascendente antes de adquirir locks para prevenir deadlocks
        List<ReservationItemRequest> sortedItems = new ArrayList<>(request.items());
        sortedItems.sort(Comparator.comparing(ReservationItemRequest::equipmentId));

        List<Inventory> inventoriesToUpdate = new ArrayList<>();
        List<ProductionReservation> reservationsToSave = new ArrayList<>();
        List<ReservationItemResponse> itemResponses = new ArrayList<>();

        Map<Long, ProductionReservation> existingByEquipment = existingReservations.stream()
                .collect(Collectors.toMap(r -> r.getEquipment().getId(), r -> r, (r1, r2) -> r1));

        // D. Adquirir locks y validar TODOS los items primero
        for (ReservationItemRequest item : sortedItems) {
            Inventory inventory = inventoryRepository.findByEquipmentIdWithPessimisticWrite(item.equipmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Equipment/Inventory not found for id: " + item.equipmentId()));

            Equipment equipment = inventory.getEquipment();

            if (!equipment.getActive()) {
                throw new InactiveEquipmentException("Equipment " + item.equipmentId() + " is inactive");
            }

            if (inventory.getAvailableQty() < item.quantity()) {
                throw new InsufficientInventoryException("Insufficient stock for equipmentId " + item.equipmentId() + ". Available: " + inventory.getAvailableQty() + ", Requested: " + item.quantity());
            }

            inventoriesToUpdate.add(inventory);

            ProductionReservation reservation = existingByEquipment.get(item.equipmentId());
            if (reservation == null) {
                reservation = new ProductionReservation(request.productionId(), equipment, item.quantity());
            } else {
                // Reutilizar fila compensada previa
                reservation.setQuantity(item.quantity());
                reservation.setStatus(ReservationStatus.RESERVED);
            }
            reservationsToSave.add(reservation);
        }

        // E. Si todo es válido: actualizar y guardar
        for (int i = 0; i < sortedItems.size(); i++) {
            ReservationItemRequest itemRequest = sortedItems.get(i);
            Inventory inventory = inventoriesToUpdate.get(i);
            ProductionReservation reservation = reservationsToSave.get(i);

            inventory.setAvailableQty(inventory.getAvailableQty() - itemRequest.quantity());
            inventory.setReservedQty(inventory.getReservedQty() + itemRequest.quantity());

            inventoryRepository.save(inventory);
            reservation = reservationRepository.save(reservation);

            itemResponses.add(new ReservationItemResponse(
                    reservation.getId(),
                    reservation.getEquipment().getId(),
                    reservation.getEquipment().getName(),
                    reservation.getQuantity(),
                    reservation.getStatus().name()
            ));
        }

        return new ReservationResponse(request.productionId(), itemResponses);
    }

    @Transactional
    public List<ReservationItemResponse> compensate(Long productionId) {
        List<ProductionReservation> activeReservations = reservationRepository.findByProductionIdAndStatus(productionId, ReservationStatus.RESERVED);
        if (activeReservations.isEmpty()) {
            return List.of();
        }

        List<ProductionReservation> sorted = new ArrayList<>(activeReservations);
        sorted.sort(Comparator.comparing(r -> r.getEquipment().getId()));

        List<ReservationItemResponse> responses = new ArrayList<>();
        for (ProductionReservation res : sorted) {
            Inventory inventory = inventoryRepository.findByEquipmentIdWithPessimisticWrite(res.getEquipment().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for equipment id: " + res.getEquipment().getId()));

            inventory.setAvailableQty(inventory.getAvailableQty() + res.getQuantity());
            inventory.setReservedQty(Math.max(0, inventory.getReservedQty() - res.getQuantity()));
            inventoryRepository.save(inventory);

            res.setStatus(ReservationStatus.COMPENSATED);
            ProductionReservation saved = reservationRepository.save(res);

            responses.add(new ReservationItemResponse(
                    saved.getId(),
                    saved.getEquipment().getId(),
                    saved.getEquipment().getName(),
                    saved.getQuantity(),
                    saved.getStatus().name()
            ));
        }

        return responses;
    }

    @Transactional(readOnly = true)
    public List<ReservationItemResponse> getReservationsByProduction(Long productionId) {
        return reservationRepository.findByProductionId(productionId).stream()
                .map(r -> new ReservationItemResponse(
                        r.getId(),
                        r.getEquipment().getId(),
                        r.getEquipment().getName(),
                        r.getQuantity(),
                        r.getStatus().name()
                ))
                .toList();
    }
}
