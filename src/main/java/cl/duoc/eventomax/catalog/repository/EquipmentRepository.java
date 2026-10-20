package cl.duoc.eventomax.catalog.repository;

import cl.duoc.eventomax.catalog.domain.Equipment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {
}
