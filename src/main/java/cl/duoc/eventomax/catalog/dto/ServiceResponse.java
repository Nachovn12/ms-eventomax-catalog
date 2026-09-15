package cl.duoc.eventomax.catalog.dto;

import java.math.BigDecimal;

public record ServiceResponse(
    Long id,
    String name,
    String description,
    BigDecimal rate,
    Boolean active
) {
}
