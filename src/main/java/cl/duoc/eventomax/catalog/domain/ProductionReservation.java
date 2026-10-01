package cl.duoc.eventomax.catalog.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "production_reservation")
public class ProductionReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long productionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    private Integer quantity;

    @Enumerated(EnumType.STRING)
    private ReservationStatus status = ReservationStatus.RESERVED;

    protected ProductionReservation() {}

    public ProductionReservation(Long productionId, Equipment equipment, Integer quantity) {
        this.productionId = productionId;
        this.equipment = equipment;
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public Long getProductionId() { return productionId; }
    public Equipment getEquipment() { return equipment; }
    public Integer getQuantity() { return quantity; }
    public ReservationStatus getStatus() { return status; }
    public void setStatus(ReservationStatus status) { this.status = status; }
}
