package cl.duoc.eventomax.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response object containing equipment details and inventory")
public record EquipmentResponse(
    Long id,
    String name,
    String description,
    Boolean active,
    Integer availableQty,
    Integer reservedQty,
    String imageUrl
) {
    public EquipmentResponse(Long id, String name, String description, Boolean active, Integer availableQty, Integer reservedQty) {
        this(id, name, description, active, availableQty, reservedQty, null);
    }
}
