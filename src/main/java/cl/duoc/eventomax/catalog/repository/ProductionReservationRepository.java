package cl.duoc.eventomax.catalog.repository;

import cl.duoc.eventomax.catalog.domain.ProductionReservation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductionReservationRepository extends JpaRepository<ProductionReservation, Long> {
    boolean existsByProductionIdAndEquipmentId(Long productionId, Long equipmentId);
}
