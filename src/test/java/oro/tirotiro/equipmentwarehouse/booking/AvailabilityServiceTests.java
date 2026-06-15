package oro.tirotiro.equipmentwarehouse.booking;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import oro.tirotiro.equipmentwarehouse.booking.persistence.Booking;
import oro.tirotiro.equipmentwarehouse.booking.persistence.BookingLine;
import oro.tirotiro.equipmentwarehouse.booking.persistence.BookingStatus;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentCategory;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentItem;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentUnit;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentUnitStatus;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.TrackingMode;

class AvailabilityServiceTests {

    private static final Instant START = Instant.parse("2026-06-15T09:00:00Z");
    private static final Instant END = Instant.parse("2026-06-15T10:00:00Z");

    private final AvailabilityService availabilityService = new AvailabilityService(null, null, null);

    @Test
    void rejectsQuantityRequestWhenOverlappingBookingsConsumeStock() {
        EquipmentItem item = item(TrackingMode.QUANTITY, 2);
        Booking existing = booking();
        existing.getLines().add(new BookingLine(existing, item, null, 1));

        assertThatThrownBy(() -> availabilityService.assertAvailable(
                List.of(item),
                List.of(),
                List.of(new AvailabilityService.RequestedLine(item.getId(), null, 2)),
                START,
                END,
                List.of(existing),
                null))
                .isInstanceOf(AvailabilityException.class)
                .hasMessageContaining("Insufficient quantity");
    }

    @Test
    void cancelledBookingsDoNotBlockQuantityAvailability() {
        EquipmentItem item = item(TrackingMode.QUANTITY, 2);
        Booking cancelled = booking();
        cancelled.cancel(null, "changed", START.minusSeconds(60));
        cancelled.getLines().add(new BookingLine(cancelled, item, null, 2));

        availabilityService.assertAvailable(
                List.of(item),
                List.of(),
                List.of(new AvailabilityService.RequestedLine(item.getId(), null, 2)),
                START,
                END,
                List.of(cancelled),
                null);
    }

    @Test
    void rejectsUnavailableUnitRequest() {
        EquipmentItem item = item(TrackingMode.UNIT, 1);
        EquipmentUnit unit = unit(item, EquipmentUnitStatus.MAINTENANCE);

        assertThatThrownBy(() -> availabilityService.assertAvailable(
                List.of(item),
                List.of(unit),
                List.of(new AvailabilityService.RequestedLine(item.getId(), unit.getId(), 1)),
                START,
                END,
                List.of(),
                null))
                .isInstanceOf(AvailabilityException.class)
                .hasMessageContaining("Equipment unit is not available");
    }

    private EquipmentItem item(TrackingMode trackingMode, int totalQuantity) {
        EquipmentCategory category = new EquipmentCategory("Cameras", null);
        ReflectionTestUtils.setField(category, "id", UUID.randomUUID());
        EquipmentItem item = new EquipmentItem(category, "Camera", trackingMode, totalQuantity);
        ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
        return item;
    }

    private EquipmentUnit unit(EquipmentItem item, EquipmentUnitStatus status) {
        EquipmentUnit unit = new EquipmentUnit(item, "INV-1", "good", status);
        ReflectionTestUtils.setField(unit, "id", UUID.randomUUID());
        return unit;
    }

    private Booking booking() {
        Booking booking = new Booking(null, START.minusSeconds(60), END.plusSeconds(60), BookingStatus.BOOKED);
        ReflectionTestUtils.setField(booking, "id", UUID.randomUUID());
        return booking;
    }
}
