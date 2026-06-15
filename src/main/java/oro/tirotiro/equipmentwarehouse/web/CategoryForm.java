package oro.tirotiro.equipmentwarehouse.web;

import jakarta.validation.constraints.NotBlank;

import oro.tirotiro.equipmentwarehouse.inventory.InventoryService;

public class CategoryForm {

    @NotBlank
    private String name;

    private String description;

    public InventoryService.CreateCategoryCommand toCommand() {
        return new InventoryService.CreateCategoryCommand(name, description);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
