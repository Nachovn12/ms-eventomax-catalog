package cl.duoc.eventomax.catalog.service;

import cl.duoc.eventomax.catalog.domain.Service;
import cl.duoc.eventomax.catalog.dto.ServiceRequest;
import cl.duoc.eventomax.catalog.dto.ServiceResponse;
import cl.duoc.eventomax.catalog.repository.ServiceRepository;
import cl.duoc.eventomax.catalog.controller.ResourceNotFoundException;
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

    @Transactional
    public ServiceResponse createService(ServiceRequest request) {
        Service service = new Service();
        service.setName(request.name());
        service.setDescription(request.description());
        service.setRate(request.rate());
        service.setActive(request.active());
        service.setImageUrl(normalizeImageUrl(request.imageUrl()));

        Service savedService = repository.save(service);
        return mapToResponse(savedService);
    }

    private ServiceResponse mapToResponse(Service service) {
        return new ServiceResponse(
                service.getId(),
                service.getName(),
                service.getDescription(),
                service.getRate(),
                service.getActive(),
                service.getImageUrl()
        );
    }

    public ServiceResponse getServiceById(Long id) {
        return repository.findById(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + id));
    }

    @Transactional
    public ServiceResponse updateService(Long id, ServiceRequest request) {
        return repository.findById(id).map(service -> {
            service.setName(request.name());
            service.setDescription(request.description());
            service.setRate(request.rate());
            service.setActive(request.active());
            if (request.imageUrl() != null) {
                service.setImageUrl(normalizeImageUrl(request.imageUrl()));
            }
            return mapToResponse(repository.save(service));
        }).orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + id));
    }

    private String normalizeImageUrl(String imageUrl) {
        if (imageUrl == null) {
            return null;
        }
        String trimmed = imageUrl.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
