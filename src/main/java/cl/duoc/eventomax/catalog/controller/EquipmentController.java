package cl.duoc.eventomax.catalog.controller;

import cl.duoc.eventomax.catalog.dto.EquipmentRequest;
import cl.duoc.eventomax.catalog.dto.EquipmentResponse;
import cl.duoc.eventomax.catalog.dto.EquipmentUpdateRequest;
import cl.duoc.eventomax.catalog.service.EquipmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/catalog/equipment")
@Tag(name = "Equipment API", description = "Operations for managing equipment and inventory")
public class EquipmentController {

    private final EquipmentService equipmentService;

    public EquipmentController(EquipmentService equipmentService) {
        this.equipmentService = equipmentService;
    }

    @PostMapping
    @Operation(summary = "Create new equipment")
    @ApiResponse(responseCode = "201", description = "Equipment created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid payload")
    public ResponseEntity<EquipmentResponse> createEquipment(@Valid @RequestBody EquipmentRequest request) {
        EquipmentResponse response = equipmentService.createEquipment(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "List all equipment")
    public ResponseEntity<List<EquipmentResponse>> listEquipment() {
        return ResponseEntity.ok(equipmentService.listEquipment());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get equipment by ID")
    @ApiResponse(responseCode = "200", description = "Equipment found")
    @ApiResponse(responseCode = "404", description = "Equipment not found")
    public ResponseEntity<EquipmentResponse> getEquipmentById(@PathVariable Long id) {
        return ResponseEntity.ok(equipmentService.getEquipmentById(id));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update equipment and available inventory",
            description = "Updates equipment details (name, description, active status) and sets available stock without altering reserved stock. Requires Admin role when accessed through BFF.")
    @ApiResponse(responseCode = "200", description = "Equipment updated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid payload or negative quantity")
    @ApiResponse(responseCode = "404", description = "Equipment not found")
    public ResponseEntity<EquipmentResponse> updateEquipment(
            @PathVariable Long id,
            @Valid @RequestBody EquipmentUpdateRequest request) {
        EquipmentResponse response = equipmentService.updateEquipment(id, request);
        return ResponseEntity.ok(response);
    }
}
