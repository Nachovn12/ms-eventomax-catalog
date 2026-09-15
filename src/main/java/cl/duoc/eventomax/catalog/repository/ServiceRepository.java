package cl.duoc.eventomax.catalog.repository;

import cl.duoc.eventomax.catalog.domain.Service;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ServiceRepository extends JpaRepository<Service, Long> {
}
