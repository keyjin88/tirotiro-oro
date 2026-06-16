package oro.tirotiro.equipmentwarehouse.web;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import oro.tirotiro.equipmentwarehouse.inventory.InventoryService;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.TrackingMode;

public class EquipmentForm {

    @NotNull
    private UUID categoryId;

    @NotBlank
    private String name;

    private String manufacturer;

    private String model;

    private String description;

    @NotNull
    private TrackingMode trackingMode = TrackingMode.QUANTITY;

    @Min(0)
    private int totalQuantity = 1;

    @NotNull
    private UUID ownerUserId;

    public InventoryService.CreateItemCommand toCommand() {
        return new InventoryService.CreateItemCommand(
                categoryId,
                name,
                manufacturer,
                model,
                description,
                trackingMode,
                totalQuantity,
                ownerUserId);
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TrackingMode getTrackingMode() {
        return trackingMode;
    }

    public void setTrackingMode(TrackingMode trackingMode) {
        this.trackingMode = trackingMode;
    }

    public int getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(int totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(UUID ownerUserId) {
        this.ownerUserId = ownerUserId;
    }
}
