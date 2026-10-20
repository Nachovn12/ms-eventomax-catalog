package cl.duoc.eventomax.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Request object for creating equipment")
public record EquipmentRequest(
    @NotBlank
    @Size(max = 150)
    @Schema(description = "Name of the equipment", example = "Projector")
    String name,

    @Size(max = 500)
    @Schema(description = "Description of the equipment", example = "4K Projector")
    String description,

    @NotNull
    @Schema(description = "Whether the equipment is active", example = "true")
    Boolean active,

    @NotNull
    @Min(0)
    @Schema(description = "Initial available quantity", example = "10")
    Integer initialAvailableQty
) {}
