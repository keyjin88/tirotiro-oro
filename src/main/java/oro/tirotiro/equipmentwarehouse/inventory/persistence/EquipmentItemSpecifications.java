package oro.tirotiro.equipmentwarehouse.inventory.persistence;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

public final class EquipmentItemSpecifications {

    private EquipmentItemSpecifications() {
    }

    public static Specification<EquipmentItem> fromCriteria(EquipmentSearchCriteria criteria) {
        Specification<EquipmentItem> spec = (root, query, cb) -> cb.conjunction();
        if (criteria.active() != null) {
            spec = spec.and(activeEquals(criteria.active()));
        }
        if (criteria.categoryId() != null) {
            spec = spec.and(categoryIdEquals(criteria.categoryId()));
        }
        if (criteria.trackingMode() != null) {
            spec = spec.and(trackingModeEquals(criteria.trackingMode()));
        }
        if (criteria.search() != null && !criteria.search().isBlank()) {
            spec = spec.and(matchesSearch(criteria.search().trim()));
        }
        return spec;
    }

    private static Specification<EquipmentItem> activeEquals(boolean active) {
        return (root, query, cb) -> cb.equal(root.get("active"), active);
    }

    private static Specification<EquipmentItem> categoryIdEquals(java.util.UUID categoryId) {
        return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    private static Specification<EquipmentItem> trackingModeEquals(TrackingMode trackingMode) {
        return (root, query, cb) -> cb.equal(root.get("trackingMode"), trackingMode);
    }

    private static Specification<EquipmentItem> matchesSearch(String queryText) {
        String pattern = "%" + queryText.toLowerCase() + "%";
        return (root, query, cb) -> {
            Join<EquipmentItem, EquipmentCategory> category = root.join("category");
            Subquery<Long> unitExists = query.subquery(Long.class);
            Root<EquipmentUnit> unit = unitExists.from(EquipmentUnit.class);
            unitExists.select(cb.literal(1L));
            unitExists.where(
                    cb.equal(unit.get("equipmentItem"), root),
                    cb.isFalse(unit.get("archived")),
                    cb.or(
                            cb.like(cb.lower(unit.get("inventoryNumber")), pattern),
                            cb.like(cb.lower(unit.get("serialNumber")), pattern)));
            return cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("manufacturer")), pattern),
                    cb.like(cb.lower(root.get("model")), pattern),
                    cb.like(cb.lower(category.get("name")), pattern),
                    cb.exists(unitExists));
        };
    }
}
