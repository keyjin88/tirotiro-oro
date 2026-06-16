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
@Table(name = "equipment_units")
public class EquipmentUnit extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_item_id", nullable = false)
    private EquipmentItem equipmentItem;

    @Column(name = "inventory_number", nullable = false, unique = true, length = 100)
    private String inventoryNumber;

    @Column(name = "serial_number", length = 100)
    private String serialNumber;

    @Column(nullable = false, length = 50)
    private String condition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private EquipmentUnitStatus status;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deleted_by")
    private User deletedBy;

    @Column(name = "delete_reason", columnDefinition = "text")
    private String deleteReason;

    protected EquipmentUnit() {
    }

    public EquipmentUnit(EquipmentItem equipmentItem, String inventoryNumber, String condition, EquipmentUnitStatus status) {
        this.equipmentItem = equipmentItem;
        this.inventoryNumber = inventoryNumber;
        this.condition = condition;
        this.status = status;
    }

    public void updateDetails(String serialNumber, String condition, EquipmentUnitStatus status, String notes) {
        this.serialNumber = serialNumber;
        this.condition = condition;
        this.status = status;
        this.notes = notes;
    }

    public void archive() {
        this.archived = true;
    }

    public void softDelete(User deletedBy, String deleteReason, Instant deletedAt) {
        this.archived = true;
        this.deletedBy = deletedBy;
        this.deleteReason = deleteReason;
        this.deletedAt = deletedAt;
    }

    public UUID getId() {
        return id;
    }

    public EquipmentItem getEquipmentItem() {
        return equipmentItem;
    }

    public String getInventoryNumber() {
        return inventoryNumber;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public String getCondition() {
        return condition;
    }

    public EquipmentUnitStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public boolean isArchived() {
        return archived;
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
