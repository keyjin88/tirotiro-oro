package oro.tirotiro.equipmentwarehouse.web;

import java.util.Comparator;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import oro.tirotiro.equipmentwarehouse.auth.persistence.User;
import oro.tirotiro.equipmentwarehouse.inventory.CatalogActiveFilter;
import oro.tirotiro.equipmentwarehouse.inventory.EquipmentCatalogFilter;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentCategoryRepository;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.TrackingMode;

@Component
public class EquipmentCatalogFilterSupport {

    private final EquipmentCategoryRepository categoryRepository;

    public EquipmentCatalogFilterSupport(EquipmentCategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public EquipmentCatalogFilter parse(
            String search,
            UUID categoryId,
            TrackingMode trackingMode,
            CatalogActiveFilter activeFilter) {
        return EquipmentCatalogFilter.of(
                search,
                categoryId,
                trackingMode,
                activeFilter == null ? CatalogActiveFilter.ACTIVE : activeFilter);
    }

    public void addFilterModel(Model model, User actor, EquipmentCatalogFilter filter) {
        model.addAttribute("equipmentFilter", filter);
        model.addAttribute("filterCategories", categoryRepository.findAll().stream()
                .sorted(Comparator.comparing(category -> category.getName().toLowerCase()))
                .toList());
        model.addAttribute("filterTrackingModes", TrackingMode.values());
        model.addAttribute("filterActiveOptions", CatalogActiveFilter.values());
    }
}
