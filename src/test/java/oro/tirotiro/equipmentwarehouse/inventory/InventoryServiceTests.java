package oro.tirotiro.equipmentwarehouse.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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

class InventoryServiceTests {

    private static final Instant NOW = Instant.parse("2026-06-15T09:00:00Z");

    private final EquipmentCategoryRepository categoryRepository = mock(EquipmentCategoryRepository.class);
    private final EquipmentItemRepository itemRepository = mock(EquipmentItemRepository.class);
    private final EquipmentUnitRepository unitRepository = mock(EquipmentUnitRepository.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final AuditService auditService = mock(AuditService.class);
    private final InventoryService inventoryService = new InventoryService(
            categoryRepository,
            itemRepository,
            unitRepository,
            permissionService,
            auditService,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void createsCategoryAndRecordsAudit() {
        User actor = actor();
        when(categoryRepository.save(any(EquipmentCategory.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        EquipmentCategory category = inventoryService.createCategory(
                new InventoryService.CreateCategoryCommand(" Cameras ", "Optics"),
                actor);

        assertThat(category.getName()).isEqualTo("Cameras");
        verify(auditService).record(eq(actor), eq("EQUIPMENT_CATEGORY_CREATED"), eq("EQUIPMENT_CATEGORY"),
                eq(category.getId()), any());
    }

    @Test
    void createsQuantityItemWhenActorCanCreateEquipment() {
        User actor = actor();
        EquipmentCategory category = category();
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(itemRepository.save(any(EquipmentItem.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        EquipmentItem item = inventoryService.createItem(
                new InventoryService.CreateItemCommand(
                        category.getId(), "Camera", "Sony", "FX", "Body", TrackingMode.QUANTITY, 2),
                actor);

        assertThat(item.getTrackingMode()).isEqualTo(TrackingMode.QUANTITY);
        assertThat(item.getTotalQuantity()).isEqualTo(2);
        verify(permissionService).requireEquipmentCreate(actor);
        verify(auditService).record(eq(actor), eq("EQUIPMENT_ITEM_CREATED"), eq("EQUIPMENT_ITEM"), eq(item.getId()), any());
    }

    @Test
    void rejectsInvalidQuantityAndMissingText() {
        EquipmentCategory category = category();
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> inventoryService.createItem(
                new InventoryService.CreateItemCommand(
                        category.getId(), "Camera", null, null, null, TrackingMode.QUANTITY, 0),
                actor()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("положительное общее количество");

        assertThatThrownBy(() -> inventoryService.createCategory(
                new InventoryService.CreateCategoryCommand(" ", null),
                actor()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Название категории обязательно");
    }

    @Test
    void updatesQuantityAndArchivesItem() {
        User actor = actor();
        EquipmentItem item = item(TrackingMode.QUANTITY);
        when(itemRepository.findById(item.getId())).thenReturn(Optional.of(item));

        inventoryService.changeQuantity(item.getId(), 5, actor);
        inventoryService.softDeleteItem(item.getId(), "broken", actor);

        assertThat(item.getTotalQuantity()).isEqualTo(5);
        assertThat(item.isActive()).isFalse();
        assertThat(item.getDeleteReason()).isEqualTo("broken");
        verify(auditService).record(eq(actor), eq("EQUIPMENT_QUANTITY_CHANGED"), eq("EQUIPMENT_ITEM"), eq(item.getId()), any());
        verify(auditService).record(eq(actor), eq("EQUIPMENT_ITEM_DELETED"), eq("EQUIPMENT_ITEM"), eq(item.getId()), any());
    }

    @Test
    void createsUpdatesAndArchivesUnitTrackedUnits() {
        User actor = actor();
        EquipmentItem item = item(TrackingMode.UNIT);
        when(itemRepository.findById(item.getId())).thenReturn(Optional.of(item));
        when(unitRepository.save(any(EquipmentUnit.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        EquipmentUnit unit = inventoryService.createUnit(
                item.getId(),
                new InventoryService.CreateUnitCommand("INV-1", "S1", "Ready", EquipmentUnitStatus.AVAILABLE, "Shelf"),
                actor);
        when(unitRepository.findById(unit.getId())).thenReturn(Optional.of(unit));

        inventoryService.updateUnit(unit.getId(),
                new InventoryService.UpdateUnitCommand("S2", "Checked", EquipmentUnitStatus.MAINTENANCE, "Repair"),
                actor);
        inventoryService.softDeleteUnit(unit.getId(), "retired", actor);

        assertThat(unit.getSerialNumber()).isEqualTo("S2");
        assertThat(unit.getStatus()).isEqualTo(EquipmentUnitStatus.MAINTENANCE);
        assertThat(unit.isArchived()).isTrue();
        verify(auditService).record(eq(actor), eq("EQUIPMENT_UNIT_CREATED"), eq("EQUIPMENT_UNIT"), eq(unit.getId()), any());
        verify(auditService).record(eq(actor), eq("EQUIPMENT_UNIT_UPDATED"), eq("EQUIPMENT_UNIT"), eq(unit.getId()), any());
        verify(auditService).record(eq(actor), eq("EQUIPMENT_UNIT_DELETED"), eq("EQUIPMENT_UNIT"), eq(unit.getId()), any());
    }

    @Test
    void rejectsUnitsForQuantityTrackedItems() {
        EquipmentItem item = item(TrackingMode.QUANTITY);
        when(itemRepository.findById(item.getId())).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> inventoryService.createUnit(
                item.getId(),
                new InventoryService.CreateUnitCommand("INV-1", null, "Ready", EquipmentUnitStatus.AVAILABLE, null),
                actor()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Единицы можно добавлять");
    }

    private User actor() {
        User user = new User("actor@example.com", "hash", "Actor");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    private EquipmentCategory category() {
        return withId(new EquipmentCategory("Cameras", null));
    }

    private EquipmentItem item(TrackingMode trackingMode) {
        return withId(new EquipmentItem(category(), "Camera", trackingMode, trackingMode == TrackingMode.QUANTITY ? 2 : 0));
    }

    private <T> T withId(T entity) {
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        return entity;
    }
}
