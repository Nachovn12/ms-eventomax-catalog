package cl.duoc.eventomax.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Request object for updating equipment and available inventory")
public record EquipmentUpdateRequest(
    @NotBlank
    @Size(max = 150)
    @Schema(description = "Name of the equipment", example = "Consola de Sonido Digital 32 Ch")
    String name,

    @Size(max = 500)
    @Schema(description = "Description of the equipment", example = "Consola digital para sonido en vivo y monitoreo")
    String description,

    @NotNull
    @Schema(description = "Whether the equipment is active (logical deactivation)", example = "true")
    Boolean active,

    @Min(0)
    @Schema(description = "Updated available quantity (optional, retains existing if omitted)", example = "15")
    Integer availableQty
) {}

