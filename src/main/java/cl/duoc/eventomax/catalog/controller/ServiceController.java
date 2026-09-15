package cl.duoc.eventomax.catalog.controller;

import cl.duoc.eventomax.catalog.dto.ServiceRequest;
import cl.duoc.eventomax.catalog.dto.ServiceResponse;
import cl.duoc.eventomax.catalog.service.CatalogService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/catalog/services")
public class ServiceController {

    private final CatalogService catalogService;

    public ServiceController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public List<ServiceResponse> listServices() {
        return catalogService.listServices();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceResponse createService(@Valid @RequestBody ServiceRequest request) {
        return catalogService.createService(request);
    }
}
