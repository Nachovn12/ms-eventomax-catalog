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
import jakarta.persistence.LockModeType;
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
    private final EntityManager entityManager;

    public InventoryReservationService(
            InventoryRepository inventoryRepository,
            ProductionReservationRepository reservationRepository,
            EntityManager entityManager) {
        this.inventoryRepository = inventoryRepository;
        this.reservationRepository = reservationRepository;
        this.entityManager = entityManager;
    }

    private void acquireProductionLock(Long productionId) {
        if (entityManager != null && productionId != null) {
            try {
                var query = entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(:id)");
                if (query != null) {
                    query.setParameter("id", productionId);
                    query.getResultList();
                }
            } catch (Exception e) {
                if (!(e instanceof UnsupportedOperationException || e instanceof NullPointerException)) {
                    throw e;
                }
            }
        }
    }

    @Transactional
    public ReservationResponse reserve(ReservationRequest request) {
        return reserve(request, null);
    }

    @Transactional
    public ReservationResponse reserve(ReservationRequest request, Long generation) {
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

        // B. Serializar operaciones para la misma producción a nivel transaccional
        acquireProductionLock(request.productionId());
        if (!checkGeneration(request.productionId(), generation, false)) {
            throw new ReservationConflictException("Reservation generation is closed or stale");
        }

        // C. Fast-path: Verificar reservas existentes para esta productionId antes de adquirir locks
        List<ProductionReservation> existingReservations = reservationRepository.findByProductionId(request.productionId());
        List<ProductionReservation> activeReservations = existingReservations.stream()
                .filter(r -> r.getStatus() == ReservationStatus.RESERVED)
                .toList();

        if (!activeReservations.isEmpty()) {
            return resolveExistingReservation(request, activeReservations);
        }

        // D. Ordenar los items por equipmentId ascendente antes de adquirir locks para prevenir deadlocks
        List<ReservationItemRequest> sortedItems = new ArrayList<>(request.items());
        sortedItems.sort(Comparator.comparing(ReservationItemRequest::equipmentId));

        List<Inventory> inventoriesToUpdate = new ArrayList<>();
        List<ProductionReservation> reservationsToSave = new ArrayList<>();
        List<ReservationItemResponse> itemResponses = new ArrayList<>();

        // E. Adquirir locks pesimistas en Inventory en orden ascendente y validar disponibilidad
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
        }

        // F. Re-verificación de idempotencia bajo lock pesimista:
        List<ProductionReservation> freshReservations = reservationRepository.findByProductionId(request.productionId());
        List<ProductionReservation> freshActive = freshReservations.stream()
                .filter(r -> r.getStatus() == ReservationStatus.RESERVED)
                .toList();

        if (!freshActive.isEmpty()) {
            return resolveExistingReservation(request, freshActive);
        }

        Map<Long, ProductionReservation> existingByEquipment = freshReservations.stream()
                .collect(Collectors.toMap(r -> r.getEquipment().getId(), r -> r, (r1, r2) -> r1));

        for (int i = 0; i < sortedItems.size(); i++) {
            ReservationItemRequest item = sortedItems.get(i);
            Inventory inventory = inventoriesToUpdate.get(i);
            Equipment equipment = inventory.getEquipment();

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

        // G. Si todo es válido: actualizar inventario y guardar reservas
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


    /** Called under the same transaction and per-production advisory lock as stock changes. */
    private boolean checkGeneration(Long productionId, Long generation, boolean compensate) {
        if (generation != null && generation <= 0) {
            throw new IllegalArgumentException("Reservation generation must be positive");
        }
        // Legacy operations are supported only until a production adopts the fenced protocol.
        // No generation may bypass a persisted tombstone, including an old unversioned client.
        if (generation == null) {
            if (entityManager != null) {
                var query = entityManager.createNativeQuery(
                        "SELECT generation FROM reservation_generations WHERE production_id = :id");
                if (query != null && !query.setParameter("id", productionId).getResultList().isEmpty()) {
                    throw new ReservationConflictException("Reservation generation is required for this production");
                }
            }
            return true;
        }
        var rows = entityManager.createNativeQuery(
                "SELECT generation, closed FROM reservation_generations WHERE production_id = :id")
                .setParameter("id", productionId).getResultList();
        if (!rows.isEmpty()) {
            Object[] row = (Object[]) rows.getFirst();
            long current = ((Number) row[0]).longValue();
            boolean closed = (Boolean) row[1];
            if (generation < current) return false;
            if (generation == current) {
                if (closed) return false;
                if (compensate) {
                    entityManager.createNativeQuery(
                            "UPDATE reservation_generations SET closed = true WHERE production_id = :id")
                            .setParameter("id", productionId).executeUpdate();
                }
                return true;
            }
            // generation > current: A future compensation or reservation must not touch an active previous generation
            if (!closed) {
                throw new ReservationConflictException("Previous reservation generation is still active");
            }
            if (compensate) {
                entityManager.createNativeQuery(
                        "UPDATE reservation_generations SET generation = :generation, closed = true WHERE production_id = :id")
                        .setParameter("id", productionId)
                        .setParameter("generation", generation)
                        .executeUpdate();
                return false; // Prior generation was already closed, and future generation never reserved
            }
        } else if (!reservationRepository.findByProductionIdAndStatus(productionId, ReservationStatus.RESERVED).isEmpty()) {
            throw new ReservationConflictException("Legacy active reservation requires reconciliation before generation adoption");
        }
        entityManager.createNativeQuery("INSERT INTO reservation_generations (production_id, generation, closed) "
                + "VALUES (:id, :generation, :closed) ON CONFLICT (production_id) DO UPDATE "
                + "SET generation = EXCLUDED.generation, closed = EXCLUDED.closed")
                .setParameter("id", productionId).setParameter("generation", generation)
                .setParameter("closed", compensate).executeUpdate();
        return true;
    }

    private ReservationResponse resolveExistingReservation(ReservationRequest request, List<ProductionReservation> activeReservations) {
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

    @Transactional
    public List<ReservationItemResponse> compensate(Long productionId) {
        return compensate(productionId, null);
    }

    @Transactional
    public List<ReservationItemResponse> compensate(Long productionId, Long generation) {
        if (productionId == null) {
            throw new IllegalArgumentException("productionId cannot be null");
        }

        // 1. Serializar operaciones de compensación para la misma producción a nivel transaccional
        acquireProductionLock(productionId);
        if (!checkGeneration(productionId, generation, true)) {
            return List.of(); // A delayed compensation must never release a newer reservation.
        }

        // 2. Consultar reservas activas iniciales para la producción
        List<ProductionReservation> activeReservations = reservationRepository.findByProductionIdAndStatus(productionId, ReservationStatus.RESERVED);
        if (activeReservations.isEmpty()) {
            return List.of();
        }

        // 3. Ordenar por equipmentId ascendente para mantener orden global consistente con reserve() y prevenir deadlocks
        List<ProductionReservation> sorted = new ArrayList<>(activeReservations);
        sorted.sort(Comparator.comparing(r -> r.getEquipment().getId()));

        List<ReservationItemResponse> responses = new ArrayList<>();
        for (ProductionReservation res : sorted) {
            if (res.getId() == null) {
                throw new IllegalStateException("Cannot compensate reservation with null id for production " + productionId);
            }

            // 4. Adquirir lock pesimista en Inventory (mismo orden que reserve)
            Inventory inventory = inventoryRepository.findByEquipmentIdWithPessimisticWrite(res.getEquipment().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for equipment id: " + res.getEquipment().getId()));

            // 5. Sincronizar y bloquear la entidad ProductionReservation con la base de datos
            if (entityManager != null) {
                entityManager.refresh(res, LockModeType.PESSIMISTIC_WRITE);
            }

            // 6. Solo si la reserva sigue en estado RESERVED se restituye el stock (evita doble restitución e inflación)
            if (res.getStatus() == ReservationStatus.RESERVED) {
                if (inventory.getReservedQty() < res.getQuantity()) {
                    throw new IllegalStateException("Invariant violation: reservedQty (" + inventory.getReservedQty()
                            + ") is less than compensation quantity (" + res.getQuantity()
                            + ") for equipment " + res.getEquipment().getId());
                }

                inventory.setAvailableQty(inventory.getAvailableQty() + res.getQuantity());
                inventory.setReservedQty(inventory.getReservedQty() - res.getQuantity());
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
