package cl.duoc.eventomax.catalog.controller;

import cl.duoc.eventomax.catalog.dto.ServiceRequest;
import cl.duoc.eventomax.catalog.dto.ServiceResponse;
import cl.duoc.eventomax.catalog.service.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/catalog/services")
@Tag(name = "Service Catalog", description = "Endpoints for managing catalog services")
public class ServiceController {

    private final CatalogService catalogService;

    public ServiceController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    @Operation(summary = "List all services", description = "Returns a list of all available services in the catalog")
    @ApiResponse(responseCode = "200", description = "Successful operation")
    public List<ServiceResponse> listServices() {
        return catalogService.listServices();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a service by ID", description = "Returns a single service based on its ID")
    @ApiResponse(responseCode = "200", description = "Service found")
    @ApiResponse(responseCode = "404", description = "Service not found")
    public ServiceResponse getServiceById(@PathVariable Long id) {
        return catalogService.getServiceById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new service", description = "Creates a new service in the catalog")
    @ApiResponse(responseCode = "201", description = "Service created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid input")
    public ServiceResponse createService(@Valid @RequestBody ServiceRequest request) {
        return catalogService.createService(request);
    }
}
