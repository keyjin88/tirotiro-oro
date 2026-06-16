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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import oro.tirotiro.equipmentwarehouse.audit.AuditService;
import oro.tirotiro.equipmentwarehouse.auth.persistence.User;
import oro.tirotiro.equipmentwarehouse.auth.persistence.UserRepository;
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
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final AuditService auditService = mock(AuditService.class);
    private final InventoryService inventoryService = new InventoryService(
            categoryRepository,
            itemRepository,
            unitRepository,
            userRepository,
            permissionService,
            auditService,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void findCatalogForcesActiveFilterForRegularUser() {
        User actor = actor();
        when(permissionService.isAdmin(actor)).thenReturn(false);
        when(itemRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

        inventoryService.findCatalog(
                EquipmentCatalogFilter.of(null, null, null, CatalogActiveFilter.ARCHIVED),
                actor);

        verify(itemRepository).findAll(any(Specification.class), eq(Sort.by(Sort.Direction.ASC, "name")));
    }

    @Test
    void findCatalogAllowsAdminArchivedFilter() {
        User actor = actor();
        when(permissionService.isAdmin(actor)).thenReturn(true);
        when(itemRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

        inventoryService.findCatalog(
                EquipmentCatalogFilter.of("camera", null, TrackingMode.UNIT, CatalogActiveFilter.ARCHIVED),
                actor);

        verify(itemRepository).findAll(any(Specification.class), eq(Sort.by(Sort.Direction.ASC, "name")));
    }

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
        User owner = actor();
        EquipmentCategory category = category();
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(userRepository.findById(actor.getId())).thenReturn(Optional.of(owner));
        when(itemRepository.save(any(EquipmentItem.class))).thenAnswer(invocation -> {
            EquipmentItem saved = invocation.getArgument(0);
            saved.assignOwner(owner);
            return withId(saved);
        });

        EquipmentItem item = inventoryService.createItem(
                new InventoryService.CreateItemCommand(
                        category.getId(), "Camera", "Sony", "FX", "Body", TrackingMode.QUANTITY, 2, actor.getId()),
                actor);

        assertThat(item.getTrackingMode()).isEqualTo(TrackingMode.QUANTITY);
        assertThat(item.getTotalQuantity()).isEqualTo(2);
        assertThat(item.getOwner()).isEqualTo(owner);
        verify(permissionService).requireEquipmentCreate(actor);
        verify(auditService).record(eq(actor), eq("EQUIPMENT_ITEM_CREATED"), eq("EQUIPMENT_ITEM"), eq(item.getId()), any());
    }

    @Test
    void defaultsOwnerToActorWhenOwnerNotSpecified() {
        User actor = actor();
        EquipmentCategory category = category();
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(userRepository.findById(actor.getId())).thenReturn(Optional.of(actor));
        when(itemRepository.save(any(EquipmentItem.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        EquipmentItem item = inventoryService.createItem(
                new InventoryService.CreateItemCommand(
                        category.getId(), "Camera", null, null, null, TrackingMode.QUANTITY, 1, null),
                actor);

        assertThat(item.getOwner()).isEqualTo(actor);
    }

    @Test
    void rejectsDisabledOwner() {
        User actor = actor();
        User disabledOwner = actor();
        ReflectionTestUtils.setField(disabledOwner, "enabled", false);
        EquipmentCategory category = category();
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(userRepository.findById(disabledOwner.getId())).thenReturn(Optional.of(disabledOwner));

        assertThatThrownBy(() -> inventoryService.createItem(
                new InventoryService.CreateItemCommand(
                        category.getId(), "Camera", null, null, null, TrackingMode.QUANTITY, 1, disabledOwner.getId()),
                actor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("активным пользователем");
    }

    @Test
    void rejectsMissingOwner() {
        User actor = actor();
        UUID missingOwnerId = UUID.randomUUID();
        EquipmentCategory category = category();
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(userRepository.findById(missingOwnerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.createItem(
                new InventoryService.CreateItemCommand(
                        category.getId(), "Camera", null, null, null, TrackingMode.QUANTITY, 1, missingOwnerId),
                actor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Пользователь не найден");
    }

    @Test
    void rejectsInvalidQuantityAndMissingText() {
        EquipmentCategory category = category();
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> inventoryService.createItem(
                new InventoryService.CreateItemCommand(
                        category.getId(), "Camera", null, null, null, TrackingMode.QUANTITY, 0, actor().getId()),
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
    void rejectsArchivingAlreadyArchivedItem() {
        User actor = actor();
        EquipmentItem item = item(TrackingMode.QUANTITY);
        item.softDelete(actor, "old reason", NOW);
        when(itemRepository.findById(item.getId())).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> inventoryService.softDeleteItem(item.getId(), "again", actor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("уже архивировано");
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
        EquipmentItem item = withId(new EquipmentItem(category(), "Camera", trackingMode, trackingMode == TrackingMode.QUANTITY ? 2 : 0));
        item.assignOwner(actor());
        return item;
    }

    private <T> T withId(T entity) {
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        return entity;
    }
}
