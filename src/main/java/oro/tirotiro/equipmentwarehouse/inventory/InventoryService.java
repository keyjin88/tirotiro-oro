package oro.tirotiro.equipmentwarehouse.inventory;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import oro.tirotiro.equipmentwarehouse.audit.AuditService;
import oro.tirotiro.equipmentwarehouse.auth.persistence.User;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentCategory;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentCategoryRepository;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentItem;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentItemRepository;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentUnit;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentUnitRepository;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentUnitStatus;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.TrackingMode;
import oro.tirotiro.equipmentwarehouse.permission.PermissionService;

@Service
public class InventoryService {

    private final EquipmentCategoryRepository categoryRepository;
    private final EquipmentItemRepository itemRepository;
    private final EquipmentUnitRepository unitRepository;
    private final PermissionService permissionService;
    private final AuditService auditService;
    private final Clock clock;

    public InventoryService(
            EquipmentCategoryRepository categoryRepository,
            EquipmentItemRepository itemRepository,
            EquipmentUnitRepository unitRepository,
            PermissionService permissionService,
            AuditService auditService,
            Clock clock) {
        this.categoryRepository = categoryRepository;
        this.itemRepository = itemRepository;
        this.unitRepository = unitRepository;
        this.permissionService = permissionService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<EquipmentItem> findActiveCatalog() {
        return itemRepository.findByActiveTrueOrderByNameAsc();
    }

    @Transactional
    public EquipmentCategory createCategory(CreateCategoryCommand command, User actor) {
        EquipmentCategory category = categoryRepository.save(new EquipmentCategory(
                requireText(command.name(), "Название категории"),
                command.description()));
        auditService.record(actor, "EQUIPMENT_CATEGORY_CREATED", "EQUIPMENT_CATEGORY", category.getId(), Map.of(
                "name", category.getName()));
        return category;
    }

    @Transactional
    public EquipmentItem createItem(CreateItemCommand command, User actor) {
        permissionService.requireEquipmentCreate(actor);
        EquipmentCategory category = categoryRepository.findById(command.categoryId())
                .orElseThrow(() -> new IllegalArgumentException("Категория не найдена: " + command.categoryId()));
        int totalQuantity = validateQuantity(command.trackingMode(), command.totalQuantity());
        EquipmentItem item = new EquipmentItem(
                category,
                requireText(command.name(), "Название оборудования"),
                command.trackingMode(),
                totalQuantity);
        item.updateDetails(category, command.name(), command.manufacturer(), command.model(), command.description());
        EquipmentItem saved = itemRepository.save(item);
        auditService.record(actor, "EQUIPMENT_ITEM_CREATED", "EQUIPMENT_ITEM", saved.getId(), Map.of(
                "name", saved.getName(),
                "trackingMode", saved.getTrackingMode().name(),
                "totalQuantity", saved.getTotalQuantity()));
        return saved;
    }

    @Transactional
    public EquipmentItem updateItemDetails(UUID itemId, UpdateItemCommand command, User actor) {
        EquipmentItem item = requireItem(itemId);
        EquipmentCategory category = categoryRepository.findById(command.categoryId())
                .orElseThrow(() -> new IllegalArgumentException("Категория не найдена: " + command.categoryId()));
        item.updateDetails(category, requireText(command.name(), "Название оборудования"),
                command.manufacturer(), command.model(), command.description());
        auditService.record(actor, "EQUIPMENT_ITEM_UPDATED", "EQUIPMENT_ITEM", item.getId(), Map.of(
                "name", item.getName()));
        return item;
    }

    @Transactional
    public EquipmentItem changeQuantity(UUID itemId, int totalQuantity, User actor) {
        EquipmentItem item = requireItem(itemId);
        if (item.getTrackingMode() != TrackingMode.QUANTITY) {
            throw new IllegalArgumentException("Напрямую менять общее количество можно только для позиций с количественным учетом");
        }
        item.changeTotalQuantity(validateQuantity(TrackingMode.QUANTITY, totalQuantity));
        auditService.record(actor, "EQUIPMENT_QUANTITY_CHANGED", "EQUIPMENT_ITEM", item.getId(), Map.of(
                "totalQuantity", item.getTotalQuantity()));
        return item;
    }

    @Transactional
    public EquipmentItem softDeleteItem(UUID itemId, String reason, User actor) {
        EquipmentItem item = requireItem(itemId);
        item.softDelete(actor, requireText(reason, "Причина удаления"), clock.instant());
        auditService.record(actor, "EQUIPMENT_ITEM_DELETED", "EQUIPMENT_ITEM", item.getId(), Map.of(
                "reason", item.getDeleteReason()));
        return item;
    }

    @Transactional
    public EquipmentUnit createUnit(UUID itemId, CreateUnitCommand command, User actor) {
        EquipmentItem item = requireItem(itemId);
        if (item.getTrackingMode() != TrackingMode.UNIT) {
            throw new IllegalArgumentException("Единицы можно добавлять только к позициям с поштучным учетом");
        }
        EquipmentUnit unit = new EquipmentUnit(
                item,
                requireText(command.inventoryNumber(), "Инвентарный номер"),
                requireText(command.condition(), "Состояние"),
                command.status());
        unit.updateDetails(command.serialNumber(), command.condition(), command.status(), command.notes());
        EquipmentUnit saved = unitRepository.save(unit);
        auditService.record(actor, "EQUIPMENT_UNIT_CREATED", "EQUIPMENT_UNIT", saved.getId(), Map.of(
                "equipmentItemId", item.getId(),
                "inventoryNumber", saved.getInventoryNumber()));
        return saved;
    }

    @Transactional
    public EquipmentUnit updateUnit(UUID unitId, UpdateUnitCommand command, User actor) {
        EquipmentUnit unit = requireUnit(unitId);
        unit.updateDetails(command.serialNumber(), requireText(command.condition(), "Состояние"),
                command.status(), command.notes());
        auditService.record(actor, "EQUIPMENT_UNIT_UPDATED", "EQUIPMENT_UNIT", unit.getId(), Map.of(
                "status", unit.getStatus().name()));
        return unit;
    }

    @Transactional
    public EquipmentUnit softDeleteUnit(UUID unitId, String reason, User actor) {
        EquipmentUnit unit = requireUnit(unitId);
        unit.softDelete(actor, requireText(reason, "Причина удаления"), clock.instant());
        auditService.record(actor, "EQUIPMENT_UNIT_DELETED", "EQUIPMENT_UNIT", unit.getId(), Map.of(
                "reason", unit.getDeleteReason()));
        return unit;
    }

    private EquipmentItem requireItem(UUID itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Оборудование не найдено: " + itemId));
    }

    private EquipmentUnit requireUnit(UUID unitId) {
        return unitRepository.findById(unitId)
                .orElseThrow(() -> new IllegalArgumentException("Единица оборудования не найдена: " + unitId));
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " обязательно для заполнения");
        }
        return value.trim();
    }

    private int validateQuantity(TrackingMode trackingMode, int totalQuantity) {
        if (trackingMode == TrackingMode.QUANTITY && totalQuantity < 1) {
            throw new IllegalArgumentException("Для позиций с количественным учетом нужно положительное общее количество");
        }
        if (trackingMode == TrackingMode.UNIT && totalQuantity < 0) {
            throw new IllegalArgumentException("Количество позиции с поштучным учетом не должно быть отрицательным");
        }
        return totalQuantity;
    }

    public record CreateCategoryCommand(String name, String description) {
    }

    public record CreateItemCommand(
            UUID categoryId,
            String name,
            String manufacturer,
            String model,
            String description,
            TrackingMode trackingMode,
            int totalQuantity) {
    }

    public record UpdateItemCommand(
            UUID categoryId,
            String name,
            String manufacturer,
            String model,
            String description) {
    }

    public record CreateUnitCommand(
            String inventoryNumber,
            String serialNumber,
            String condition,
            EquipmentUnitStatus status,
            String notes) {
    }

    public record UpdateUnitCommand(
            String serialNumber,
            String condition,
            EquipmentUnitStatus status,
            String notes) {
    }
}
