package cl.duoc.eventomax.catalog.controller;

import cl.duoc.eventomax.catalog.dto.ReservationRequest;
import cl.duoc.eventomax.catalog.dto.ReservationResponse;
import cl.duoc.eventomax.catalog.service.InventoryReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/reservations")
@Tag(name = "Inventory Reservation API", description = "Operations for managing inventory reservations for productions")
public class ReservationController {

    private final InventoryReservationService reservationService;

    public ReservationController(InventoryReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    @Operation(summary = "Reserve inventory for a production")
    @ApiResponse(responseCode = "201", description = "Inventory reserved successfully")
    @ApiResponse(responseCode = "400", description = "Invalid payload or duplicate items")
    @ApiResponse(responseCode = "404", description = "Equipment not found")
    @ApiResponse(responseCode = "409", description = "Insufficient inventory or conflict")
    public ResponseEntity<ReservationResponse> reserve(@Valid @RequestBody ReservationRequest request) {
        ReservationResponse response = reservationService.reserve(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
