package cl.duoc.eventomax.catalog.controller;

import cl.duoc.eventomax.catalog.dto.ReservationItemResponse;
import cl.duoc.eventomax.catalog.dto.ReservationRequest;
import cl.duoc.eventomax.catalog.dto.ReservationResponse;
import cl.duoc.eventomax.catalog.service.InventoryReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/catalog/reservations")
@Tag(name = "Inventory Reservation API", description = "Operations for managing inventory reservations for productions")
public class ReservationController {

    private final InventoryReservationService reservationService;

    public ReservationController(InventoryReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    @Operation(summary = "Reserve inventory for a production (idempotent)")
    @ApiResponse(responseCode = "201", description = "Inventory reserved successfully")
    @ApiResponse(responseCode = "400", description = "Invalid payload or duplicate items")
    @ApiResponse(responseCode = "404", description = "Equipment not found")
    @ApiResponse(responseCode = "409", description = "Insufficient inventory or conflict")
    public ResponseEntity<ReservationResponse> reserve(@Valid @RequestBody ReservationRequest request,
            @RequestHeader(value = "X-Reservation-Generation", required = false) Long generation) {
        ReservationResponse response = generation == null ? reservationService.reserve(request) : reservationService.reserve(request, generation);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/production/{productionId}")
    @Operation(summary = "Get all reservations for a given production")
    @ApiResponse(responseCode = "200", description = "Reservations found")
    public ResponseEntity<List<ReservationItemResponse>> getReservationsByProduction(@PathVariable Long productionId) {
        return ResponseEntity.ok(reservationService.getReservationsByProduction(productionId));
    }

    @PostMapping("/production/{productionId}/compensate")
    @Operation(summary = "Technical compensation: release active reservations for a given production")
    @ApiResponse(responseCode = "200", description = "Reservations compensated successfully")
    public ResponseEntity<List<ReservationItemResponse>> compensate(@PathVariable Long productionId,
            @RequestHeader(value = "X-Reservation-Generation", required = false) Long generation) {
        return ResponseEntity.ok(generation == null ? reservationService.compensate(productionId) : reservationService.compensate(productionId, generation));
    }
}
