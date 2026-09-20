package cl.duoc.eventomax.catalog.service;

import cl.duoc.eventomax.catalog.domain.Service;
import cl.duoc.eventomax.catalog.dto.ServiceRequest;
import cl.duoc.eventomax.catalog.dto.ServiceResponse;
import cl.duoc.eventomax.catalog.repository.ServiceRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
public class CatalogService {

    private final ServiceRepository repository;

    public CatalogService(ServiceRepository repository) {
        this.repository = repository;
    }

    public List<ServiceResponse> listServices() {
        return repository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public ServiceResponse createService(ServiceRequest request) {
        Service service = new Service();
        service.setName(request.name());
        service.setDescription(request.description());
        service.setRate(request.rate());
        service.setActive(request.active());

        Service savedService = repository.save(service);
        return mapToResponse(savedService);
    }

    private ServiceResponse mapToResponse(Service service) {
        return new ServiceResponse(
                service.getId(),
                service.getName(),
                service.getDescription(),
                service.getRate(),
                service.getActive()
        );
    }

    @Transactional
    public ServiceResponse updateService(Long id, ServiceRequest request) {
        Service service = repository.findById(id)
                .orElseThrow(() -> new cl.duoc.eventomax.catalog.controller.ResourceNotFoundException(
                        "Service not found with id: " + id));

        service.setName(request.name());
        service.setDescription(request.description());
        service.setRate(request.rate());
        service.setActive(request.active());

        return mapToResponse(repository.save(service));
    }

    public ServiceResponse getServiceById(Long id) {
        return repository.findById(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> new cl.duoc.eventomax.catalog.controller.ResourceNotFoundException("Service not found with id: " + id));
    }
}
