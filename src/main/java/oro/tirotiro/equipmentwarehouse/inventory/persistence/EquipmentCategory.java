package oro.tirotiro.equipmentwarehouse.inventory.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import oro.tirotiro.equipmentwarehouse.persistence.AuditedEntity;

@Entity
@Table(name = "equipment_categories")
public class EquipmentCategory extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private boolean archived;

    protected EquipmentCategory() {
    }

    public EquipmentCategory(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void update(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void archive() {
        this.archived = true;
    }

    public void restore() {
        this.archived = false;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isArchived() {
        return archived;
    }
}
