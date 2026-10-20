package cl.duoc.eventomax.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReservationRequest(
    @NotNull
    @Schema(description = "Production ID requesting the reservation", example = "123")
    Long productionId,

    @NotEmpty
    @Schema(description = "List of items to reserve")
    List<@Valid ReservationItemRequest> items
) {}
