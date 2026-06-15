package oro.tirotiro.equipmentwarehouse.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import oro.tirotiro.equipmentwarehouse.booking.AvailabilityService;
import oro.tirotiro.equipmentwarehouse.config.AppProperties;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentCategory;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentItem;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentItemRepository;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.TrackingMode;

class CalendarServiceTests {

    @Test
    void buildsCalendarForSelectedCategoryAndMapsAvailability() {
        EquipmentItem camera = item("Cameras", "Camera", TrackingMode.QUANTITY);
        EquipmentItem light = item("Lights", "Light", TrackingMode.UNIT);
        Instant startsAt = Instant.parse("2026-06-15T00:00:00Z");
        Instant endsAt = Instant.parse("2026-06-17T00:00:00Z");
        EquipmentItemRepository itemRepository = mock(EquipmentItemRepository.class);
        AvailabilityService availabilityService = mock(AvailabilityService.class);
        CalendarService calendarService = new CalendarService(
                itemRepository,
                availabilityService,
                new AppProperties(ZoneId.of("UTC"), new AppProperties.Security(false)));
        when(itemRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(camera, light));
        when(availabilityService.getAvailability(List.of(camera.getId()), startsAt, endsAt)).thenReturn(List.of(
                new AvailabilityService.ItemAvailability(camera.getId(), 3, 1, 2, List.of())));

        CalendarService.CalendarView view = calendarService.availabilityCalendar(
                startsAt,
                endsAt,
                camera.getCategory().getId());

        assertThat(view.days()).extracting(CalendarService.CalendarDay::date)
                .containsExactly(java.time.LocalDate.parse("2026-06-15"), java.time.LocalDate.parse("2026-06-16"));
        assertThat(view.items()).singleElement().satisfies(item -> {
            assertThat(item.categoryName()).isEqualTo("Cameras");
            assertThat(item.equipmentName()).isEqualTo("Camera");
            assertThat(item.total()).isEqualTo(3);
            assertThat(item.booked()).isEqualTo(1);
            assertThat(item.available()).isEqualTo(2);
        });
    }

    private EquipmentItem item(String categoryName, String itemName, TrackingMode trackingMode) {
        EquipmentCategory category = new EquipmentCategory(categoryName, null);
        ReflectionTestUtils.setField(category, "id", UUID.randomUUID());
        EquipmentItem item = new EquipmentItem(category, itemName, trackingMode, trackingMode == TrackingMode.QUANTITY ? 3 : 0);
        ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
        return item;
    }
}
