package cl.duoc.eventomax.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReservationItemRequest(
    @NotNull
    @Schema(description = "ID of the equipment to reserve", example = "1")
    Long equipmentId,

    @NotNull
    @Min(1)
    @Schema(description = "Quantity to reserve", example = "5")
    Integer quantity
) {}
