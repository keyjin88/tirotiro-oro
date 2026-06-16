package oro.tirotiro.equipmentwarehouse.inventory;

import java.util.UUID;

import oro.tirotiro.equipmentwarehouse.inventory.persistence.TrackingMode;

public record EquipmentCatalogFilter(
        String search,
        UUID categoryId,
        TrackingMode trackingMode,
        CatalogActiveFilter activeFilter) {

    public static final EquipmentCatalogFilter EMPTY = new EquipmentCatalogFilter(
            null, null, null, CatalogActiveFilter.ACTIVE);

    public static EquipmentCatalogFilter of(
            String search,
            UUID categoryId,
            TrackingMode trackingMode,
            CatalogActiveFilter activeFilter) {
        return new EquipmentCatalogFilter(search, categoryId, trackingMode, activeFilter);
    }

    public boolean filterBySearch() {
        return search != null && !search.isBlank();
    }

    public boolean filterByCategory() {
        return categoryId != null;
    }

    public boolean filterByTrackingMode() {
        return trackingMode != null;
    }
}
