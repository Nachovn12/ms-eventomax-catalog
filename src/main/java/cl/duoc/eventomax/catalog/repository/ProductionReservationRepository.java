package cl.duoc.eventomax.catalog.repository;

import cl.duoc.eventomax.catalog.domain.ProductionReservation;
import cl.duoc.eventomax.catalog.domain.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProductionReservationRepository extends JpaRepository<ProductionReservation, Long> {
    boolean existsByProductionIdAndEquipmentId(Long productionId, Long equipmentId);
    List<ProductionReservation> findByProductionId(Long productionId);
    List<ProductionReservation> findByProductionIdAndStatus(Long productionId, ReservationStatus status);
}
