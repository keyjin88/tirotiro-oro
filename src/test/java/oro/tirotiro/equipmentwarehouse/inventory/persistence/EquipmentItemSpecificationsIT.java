package oro.tirotiro.equipmentwarehouse.inventory.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import oro.tirotiro.equipmentwarehouse.auth.persistence.User;
import oro.tirotiro.equipmentwarehouse.auth.persistence.UserRepository;
import oro.tirotiro.equipmentwarehouse.booking.persistence.BookingLineRepository;
import oro.tirotiro.equipmentwarehouse.booking.persistence.BookingRepository;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.liquibase.enabled=true",
        "app.time-zone=UTC",
        "app.security.remember-me-enabled=false",
        "app.bootstrap-admin.email=bootstrap-it@example.com",
        "app.bootstrap-admin.password=secret",
        "app.bootstrap-admin.name=Bootstrap IT"
})
class EquipmentItemSpecificationsIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private EquipmentCategoryRepository categoryRepository;

    @Autowired
    private EquipmentItemRepository itemRepository;

    @Autowired
    private EquipmentUnitRepository unitRepository;

    @Autowired
    private BookingLineRepository bookingLineRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    private EquipmentCategory cameras;
    private EquipmentCategory lights;

    @BeforeEach
    void setUp() {
        clearCatalog();
        User owner = bootstrapUser();
        cameras = categoryRepository.save(new EquipmentCategory("Cameras", null));
        lights = categoryRepository.save(new EquipmentCategory("Lights", null));

        EquipmentItem activeCamera = itemRepository.save(item(cameras, "Sony Camera", TrackingMode.UNIT, 0, owner));
        EquipmentItem archivedCamera = itemRepository.save(item(cameras, "Old Camera", TrackingMode.QUANTITY, 1, owner));
        archivedCamera.softDelete(null, "retired", java.time.Instant.parse("2026-06-01T00:00:00Z"));
        itemRepository.save(archivedCamera);
        itemRepository.save(item(lights, "LED Panel", TrackingMode.QUANTITY, 3, owner));

        assertThat(activeCamera.getId()).isNotNull();
    }

    @Test
    void filtersCatalogBySearchCategoryTrackingModeAndActiveStatus() {
        assertThat(itemRepository.findAll(
                EquipmentItemSpecifications.fromCriteria(new EquipmentSearchCriteria("Sony", null, null, true)),
                Sort.by("name")))
                .extracting(EquipmentItem::getName)
                .containsExactly("Sony Camera");

        assertThat(itemRepository.findAll(
                EquipmentItemSpecifications.fromCriteria(new EquipmentSearchCriteria(null, cameras.getId(), null, true)),
                Sort.by("name")))
                .extracting(EquipmentItem::getName)
                .containsExactly("Sony Camera");

        assertThat(itemRepository.findAll(
                EquipmentItemSpecifications.fromCriteria(new EquipmentSearchCriteria(null, null, TrackingMode.QUANTITY, true)),
                Sort.by("name")))
                .extracting(EquipmentItem::getName)
                .containsExactly("LED Panel");

        assertThat(itemRepository.findAll(
                EquipmentItemSpecifications.fromCriteria(new EquipmentSearchCriteria(null, null, null, false)),
                Sort.by("name")))
                .extracting(EquipmentItem::getName)
                .containsExactly("Old Camera");

        assertThat(itemRepository.findAll(
                EquipmentItemSpecifications.fromCriteria(new EquipmentSearchCriteria(null, null, null, null)),
                Sort.by("name")))
                .extracting(EquipmentItem::getName)
                .containsExactly("LED Panel", "Old Camera", "Sony Camera");
    }

    private void clearCatalog() {
        bookingLineRepository.deleteAllInBatch();
        bookingRepository.deleteAllInBatch();
        unitRepository.deleteAllInBatch();
        itemRepository.deleteAllInBatch();
        categoryRepository.deleteAllInBatch();
    }

    private User bootstrapUser() {
        return userRepository.findByEmailIgnoreCase("bootstrap-it@example.com").orElseThrow();
    }

    private EquipmentItem item(
            EquipmentCategory category,
            String name,
            TrackingMode trackingMode,
            int totalQuantity,
            User owner) {
        EquipmentItem item = new EquipmentItem(category, name, trackingMode, totalQuantity);
        item.assignOwner(owner);
        return item;
    }
}
