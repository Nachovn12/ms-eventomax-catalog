package cl.duoc.eventomax.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ServiceRequest(
    @NotBlank
    @Size(max = 150)
    String name,

    @Size(max = 500)
    String description,

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    BigDecimal rate,

    @NotNull
    Boolean active
) {
}
