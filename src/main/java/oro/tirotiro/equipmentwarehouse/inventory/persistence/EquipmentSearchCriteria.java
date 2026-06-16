package oro.tirotiro.equipmentwarehouse.inventory.persistence;

import java.util.UUID;

public record EquipmentSearchCriteria(
        String search,
        UUID categoryId,
        TrackingMode trackingMode,
        Boolean active) {

    public static EquipmentSearchCriteria unrestricted() {
        return new EquipmentSearchCriteria(null, null, null, null);
    }
}
