package cl.duoc.eventomax.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Schema(description = "Request object for creating or updating a service")
public record ServiceRequest(
    @NotBlank
    @Size(max = 150)
    @Schema(description = "Name of the service", example = "Catering Premium")
    String name,

    @Size(max = 500)
    @Schema(description = "Description of the service", example = "Buffet completo para bodas")
    String description,

    @NotNull
    @Positive
    @Digits(integer = 10, fraction = 2)
    @Schema(description = "Hourly or fixed rate of the service", example = "500.00")
    BigDecimal rate,

    @NotNull
    @Schema(description = "Whether the service is active", example = "true")
    Boolean active
) {
}
