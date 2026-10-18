package cl.duoc.eventomax.catalog.repository;

import cl.duoc.eventomax.catalog.domain.ProductionReservation;
import cl.duoc.eventomax.catalog.domain.ReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ProductionReservationRepository extends JpaRepository<ProductionReservation, Long> {
    boolean existsByProductionIdAndEquipmentId(Long productionId, Long equipmentId);
    List<ProductionReservation> findByProductionId(Long productionId);
    List<ProductionReservation> findByProductionIdAndStatus(Long productionId, ReservationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ProductionReservation r WHERE r.id = :id")
    Optional<ProductionReservation> findByIdWithPessimisticWrite(@Param("id") Long id);
}
