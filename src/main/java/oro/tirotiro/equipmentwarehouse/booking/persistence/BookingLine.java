package oro.tirotiro.equipmentwarehouse.booking.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentItem;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentUnit;

@Entity
@Table(name = "booking_lines")
public class BookingLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_item_id", nullable = false)
    private EquipmentItem equipmentItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_unit_id")
    private EquipmentUnit equipmentUnit;

    @Column(nullable = false)
    private int quantity;

    protected BookingLine() {
    }

    public BookingLine(Booking booking, EquipmentItem equipmentItem, EquipmentUnit equipmentUnit, int quantity) {
        this.booking = booking;
        this.equipmentItem = equipmentItem;
        this.equipmentUnit = equipmentUnit;
        this.quantity = quantity;
    }

    public UUID getId() {
        return id;
    }

    public Booking getBooking() {
        return booking;
    }

    public EquipmentItem getEquipmentItem() {
        return equipmentItem;
    }

    public EquipmentUnit getEquipmentUnit() {
        return equipmentUnit;
    }

    public int getQuantity() {
        return quantity;
    }
}
