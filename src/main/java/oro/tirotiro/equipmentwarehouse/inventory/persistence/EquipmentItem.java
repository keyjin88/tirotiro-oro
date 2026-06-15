package oro.tirotiro.equipmentwarehouse.inventory.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
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

import oro.tirotiro.equipmentwarehouse.auth.persistence.User;
import oro.tirotiro.equipmentwarehouse.persistence.AuditedEntity;

@Entity
@Table(name = "equipment_items")
public class EquipmentItem extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private EquipmentCategory category;

    @Column(nullable = false)
    private String name;

    private String manufacturer;

    private String model;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "tracking_mode", nullable = false, length = 20)
    private TrackingMode trackingMode;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deleted_by")
    private User deletedBy;

    @Column(name = "delete_reason", columnDefinition = "text")
    private String deleteReason;

    protected EquipmentItem() {
    }

    public EquipmentItem(EquipmentCategory category, String name, TrackingMode trackingMode, int totalQuantity) {
        this.category = category;
        this.name = name;
        this.trackingMode = trackingMode;
        this.totalQuantity = totalQuantity;
    }

    public void updateDetails(
            EquipmentCategory category,
            String name,
            String manufacturer,
            String model,
            String description) {
        this.category = category;
        this.name = name;
        this.manufacturer = manufacturer;
        this.model = model;
        this.description = description;
    }

    public void changeTotalQuantity(int totalQuantity) {
        if (totalQuantity < 0) {
            throw new IllegalArgumentException("Общее количество не должно быть отрицательным");
        }
        this.totalQuantity = totalQuantity;
    }

    public void softDelete(User deletedBy, String deleteReason, Instant deletedAt) {
        this.active = false;
        this.deletedBy = deletedBy;
        this.deleteReason = deleteReason;
        this.deletedAt = deletedAt;
    }

    public UUID getId() {
        return id;
    }

    public EquipmentCategory getCategory() {
        return category;
    }

    public String getName() {
        return name;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public String getModel() {
        return model;
    }

    public String getDescription() {
        return description;
    }

    public TrackingMode getTrackingMode() {
        return trackingMode;
    }

    public int getTotalQuantity() {
        return totalQuantity;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public User getDeletedBy() {
        return deletedBy;
    }

    public String getDeleteReason() {
        return deleteReason;
    }
}
