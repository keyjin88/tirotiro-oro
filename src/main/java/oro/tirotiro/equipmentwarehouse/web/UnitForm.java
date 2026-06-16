package oro.tirotiro.equipmentwarehouse.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import oro.tirotiro.equipmentwarehouse.inventory.InventoryService;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentUnitStatus;

public class UnitForm {

    @NotBlank
    private String inventoryNumber;

    private String serialNumber;

    @NotBlank
    private String condition = "Готово";

    @NotNull
    private EquipmentUnitStatus status = EquipmentUnitStatus.AVAILABLE;

    private String notes;

    public InventoryService.CreateUnitCommand toCommand() {
        return new InventoryService.CreateUnitCommand(inventoryNumber, serialNumber, condition, status, notes);
    }

    public String getInventoryNumber() {
        return inventoryNumber;
    }

    public void setInventoryNumber(String inventoryNumber) {
        this.inventoryNumber = inventoryNumber;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public EquipmentUnitStatus getStatus() {
        return status;
    }

    public void setStatus(EquipmentUnitStatus status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
