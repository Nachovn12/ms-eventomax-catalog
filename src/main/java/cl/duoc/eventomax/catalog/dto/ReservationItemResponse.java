package cl.duoc.eventomax.catalog.dto;

public record ReservationItemResponse(
    Long reservationId,
    Long equipmentId,
    Integer quantity,
    String status
) {}
