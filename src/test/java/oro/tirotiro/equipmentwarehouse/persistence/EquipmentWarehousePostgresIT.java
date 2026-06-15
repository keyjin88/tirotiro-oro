package oro.tirotiro.equipmentwarehouse.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import oro.tirotiro.equipmentwarehouse.booking.BookingService;
import oro.tirotiro.equipmentwarehouse.booking.persistence.Booking;
import oro.tirotiro.equipmentwarehouse.booking.persistence.BookingRepository;
import oro.tirotiro.equipmentwarehouse.booking.persistence.BookingStatus;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentCategory;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentCategoryRepository;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentItem;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentItemRepository;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.TrackingMode;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.liquibase.enabled=true",
        "app.time-zone=UTC",
        "app.security.remember-me-enabled=false"
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
    private BookingRepository bookingRepository;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private AvailabilityService availabilityService;

    @Test
    void liquibaseSchemaSupportsBookingOverlapRejectionAndCancellation() {
        User actor = adminUser();
        EquipmentCategory category = categoryRepository.save(new EquipmentCategory("Cameras", "Production cameras"));
        EquipmentItem item = itemRepository.save(new EquipmentItem(category, "Cinema Camera", TrackingMode.QUANTITY, 1));

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
                .hasMessageContaining("Insufficient quantity");

        bookingService.cancelBooking(booking.getId(), "not needed", actor);

        assertThat(availabilityService.getAvailability(List.of(item.getId()), START, END))
                .singleElement()
                .satisfies(availability -> {
                    assertThat(availability.booked()).isZero();
                    assertThat(availability.available()).isEqualTo(1);
                });
    }

    private User adminUser() {
        User user = new User("admin@example.com", "{noop}secret", "Admin");
        user.getRoles().add(roleRepository.findByCode(RoleCode.ADMIN).orElseThrow());
        return userRepository.saveAndFlush(user);
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
