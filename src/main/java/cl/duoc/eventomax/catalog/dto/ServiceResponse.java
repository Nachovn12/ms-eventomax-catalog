package cl.duoc.eventomax.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Response object representing a service")
public record ServiceResponse(
    @Schema(description = "Unique identifier of the service", example = "1")
    Long id,
    @Schema(description = "Name of the service", example = "Catering Premium")
    String name,
    @Schema(description = "Description of the service", example = "Buffet completo para bodas")
    String description,
    @Schema(description = "Hourly or fixed rate of the service", example = "500.00")
    BigDecimal rate,
    @Schema(description = "Whether the service is active", example = "true")
    Boolean active,
    @Schema(description = "Optional image URL or compressed WebP/JPEG data URL", example = "assets/catalog/line-array-meyersound.svg")
    String imageUrl
) {
    public ServiceResponse(Long id, String name, String description, BigDecimal rate, Boolean active) {
        this(id, name, description, rate, active, null);
    }
}
