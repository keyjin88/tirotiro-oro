package oro.tirotiro.equipmentwarehouse.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import oro.tirotiro.equipmentwarehouse.auth.persistence.RoleCode;
import oro.tirotiro.equipmentwarehouse.auth.persistence.RoleRepository;
import oro.tirotiro.equipmentwarehouse.auth.persistence.User;
import oro.tirotiro.equipmentwarehouse.auth.persistence.UserRepository;
import oro.tirotiro.equipmentwarehouse.booking.AvailabilityException;
import oro.tirotiro.equipmentwarehouse.booking.AvailabilityService;
import oro.tirotiro.equipmentwarehouse.booking.BookingFilter;
import oro.tirotiro.equipmentwarehouse.booking.BookingService;
import oro.tirotiro.equipmentwarehouse.booking.persistence.Booking;
import oro.tirotiro.equipmentwarehouse.booking.persistence.BookingRepository;
import oro.tirotiro.equipmentwarehouse.booking.persistence.BookingStatus;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentCategory;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentCategoryRepository;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentItem;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentItemRepository;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentUnit;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentUnitRepository;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentUnitStatus;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.TrackingMode;

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
class EquipmentWarehousePostgresIT {

    private static final Instant START = Instant.parse("2026-06-15T09:00:00Z");
    private static final Instant END = Instant.parse("2026-06-15T10:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EquipmentCategoryRepository categoryRepository;

    @Autowired
    private EquipmentItemRepository itemRepository;

    @Autowired
    private EquipmentUnitRepository unitRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private AvailabilityService availabilityService;

    @Test
    void findFilteredBookingsWithNoOptionalFiltersWorksOnPostgres() {
        User actor = adminUser();
        EquipmentCategory category = categoryRepository.save(new EquipmentCategory("Booking Filters", "Filter tests"));
        EquipmentItem item = itemRepository.save(item(category, "Filter Camera", TrackingMode.QUANTITY, 1, actor));
        EquipmentItem otherItem = itemRepository.save(item(category, "Other Camera", TrackingMode.QUANTITY, 1, actor));

        bookingService.createBooking(command(item, START, END, "filter test booking"), actor);
        bookingService.createBooking(command(otherItem, START, END, "other booking"), actor);

        assertThat(bookingService.findBookings(actor, BookingFilter.EMPTY))
                .extracting(Booking::getComment)
                .contains("filter test booking", "other booking");

        assertThat(bookingService.findBookings(actor, BookingFilter.of(null, null, null, null, item.getId())))
                .extracting(Booking::getComment)
                .containsExactly("filter test booking");
    }

    @Test
    void liquibaseSchemaSupportsBookingOverlapRejectionAndCancellation() {
        User actor = adminUser();
        EquipmentCategory category = categoryRepository.save(new EquipmentCategory("Cameras", "Production cameras"));
        EquipmentItem item = itemRepository.save(item(category, "Cinema Camera", TrackingMode.QUANTITY, 1, actor));

        Booking booking = bookingService.createBooking(
                command(item, START, END, "first booking"),
                actor);

        assertThat(booking.getId()).isNotNull();
        assertThat(bookingRepository.findOverlappingForItems(
                List.of(item.getId()),
                START.plusSeconds(60),
                END.minusSeconds(60),
                Set.of(BookingStatus.BOOKED))).hasSize(1);
        assertThat(availabilityService.getAvailability(List.of(item.getId()), START, END))
                .singleElement()
                .satisfies(availability -> {
                    assertThat(availability.total()).isEqualTo(1);
                    assertThat(availability.booked()).isEqualTo(1);
                    assertThat(availability.available()).isZero();
                });

        assertThatThrownBy(() -> bookingService.createBooking(
                command(item, START.plusSeconds(300), END.plusSeconds(300), "overlap"),
                actor))
                .isInstanceOf(AvailabilityException.class)
                .hasMessageContaining(item.getId().toString());

        bookingService.cancelBooking(booking.getId(), "not needed", actor);

        assertThat(availabilityService.getAvailability(List.of(item.getId()), START, END))
                .singleElement()
                .satisfies(availability -> {
                    assertThat(availability.booked()).isZero();
                    assertThat(availability.available()).isEqualTo(1);
                });
    }

    @Test
    void bookingEquipmentSearchMatchesCatalogAndUnitFields() {
        User owner = adminUser();
        EquipmentCategory cameras = categoryRepository.save(new EquipmentCategory("Search Cameras", "Camera category"));
        EquipmentCategory sound = categoryRepository.save(new EquipmentCategory("Search Sound", "Audio category"));

        EquipmentItem namedItem = itemRepository.save(item(
                cameras,
                "Search Lens Kit",
                "Cooke",
                "S4",
                TrackingMode.QUANTITY,
                3,
                owner));
        EquipmentItem categoryItem = itemRepository.save(item(
                sound,
                "Search Boom Pole",
                "Ambient",
                "QS",
                TrackingMode.QUANTITY,
                2,
                owner));
        EquipmentItem manufacturerItem = itemRepository.save(item(
                cameras,
                "Search Field Monitor",
                "SmallHD",
                "Indie 7",
                TrackingMode.QUANTITY,
                1,
                owner));
        EquipmentItem modelItem = itemRepository.save(item(
                cameras,
                "Search Prime Lens",
                "Canon",
                "CN-E 50",
                TrackingMode.QUANTITY,
                1,
                owner));
        EquipmentItem unitItem = itemRepository.save(item(
                cameras,
                "Search Cinema Body",
                "ARRI",
                "Alexa Mini",
                TrackingMode.UNIT,
                0,
                owner));
        EquipmentUnit unit = new EquipmentUnit(unitItem, "SEARCH-INV-001", "Хорошее", EquipmentUnitStatus.AVAILABLE);
        unit.updateDetails("SEARCH-SERIAL-001", "Хорошее", EquipmentUnitStatus.AVAILABLE, null);
        unitRepository.save(unit);

        assertSearchFinds("Lens Kit", namedItem);
        assertSearchFinds("Sound", categoryItem);
        assertSearchFinds("smallhd", manufacturerItem);
        assertSearchFinds("CN-E", modelItem);
        assertSearchFinds("inv-001", unitItem);
        assertSearchFinds("serial-001", unitItem);
        assertThat(itemRepository.searchActiveForBooking("definitely-missing", PageRequest.of(0, 20))).isEmpty();
    }

    private User adminUser() {
        return userRepository.findByEmailIgnoreCase("admin@example.com")
                .orElseGet(() -> {
                    User user = new User("admin@example.com", "{noop}secret", "Admin");
                    user.getRoles().add(roleRepository.findByCode(RoleCode.ADMIN).orElseThrow());
                    return userRepository.saveAndFlush(user);
                });
    }

    private void assertSearchFinds(String query, EquipmentItem expectedItem) {
        assertThat(itemRepository.searchActiveForBooking(query, PageRequest.of(0, 20)))
                .extracting(EquipmentItem::getId)
                .contains(expectedItem.getId());
    }

    private EquipmentItem item(
            EquipmentCategory category,
            String name,
            String manufacturer,
            String model,
            TrackingMode trackingMode,
            int totalQuantity,
            User owner) {
        EquipmentItem item = new EquipmentItem(category, name, trackingMode, totalQuantity);
        item.updateDetails(category, name, manufacturer, model, null);
        item.assignOwner(owner);
        return item;
    }

    private EquipmentItem item(
            EquipmentCategory category,
            String name,
            TrackingMode trackingMode,
            int totalQuantity,
            User owner) {
        return item(category, name, null, null, trackingMode, totalQuantity, owner);
    }

    private BookingService.CreateBookingCommand command(
            EquipmentItem item,
            Instant startsAt,
            Instant endsAt,
            String comment) {
        return new BookingService.CreateBookingCommand(
                startsAt,
                endsAt,
                comment,
                List.of(new BookingService.BookingLineCommand(item.getId(), null, 1)));
    }
}
