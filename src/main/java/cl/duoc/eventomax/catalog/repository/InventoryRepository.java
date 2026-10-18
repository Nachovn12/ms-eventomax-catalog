package cl.duoc.eventomax.catalog.repository;

import cl.duoc.eventomax.catalog.domain.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    // Consulta sin bloqueo para operaciones de solo lectura
    @Query("SELECT i FROM Inventory i WHERE i.equipment.id = :equipmentId")
    Optional<Inventory> findByEquipmentIdWithoutLock(@Param("equipmentId") Long equipmentId);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.equipment.id = :equipmentId")
    Optional<Inventory> findByEquipmentIdWithPessimisticWrite(@Param("equipmentId") Long equipmentId);
}
