package cl.duoc.eventomax.catalog.dto;

public record ReservationItemResponse(
    Long reservationId,
    Long equipmentId,
    String equipmentName,
    Integer quantity,
    String status
) {
    public ReservationItemResponse(Long reservationId, Long equipmentId, Integer quantity, String status) {
        this(reservationId, equipmentId, null, quantity, status);
    }
}
