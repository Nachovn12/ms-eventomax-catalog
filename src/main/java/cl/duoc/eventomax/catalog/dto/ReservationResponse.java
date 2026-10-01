package cl.duoc.eventomax.catalog.dto;

import java.util.List;

public record ReservationResponse(
    Long productionId,
    List<ReservationItemResponse> items
) {}
