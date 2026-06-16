package oro.tirotiro.equipmentwarehouse.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import oro.tirotiro.equipmentwarehouse.auth.persistence.User;
import oro.tirotiro.equipmentwarehouse.booking.BookingFilter;
import oro.tirotiro.equipmentwarehouse.booking.BookingService;
import oro.tirotiro.equipmentwarehouse.booking.persistence.Booking;
import oro.tirotiro.equipmentwarehouse.booking.persistence.BookingLine;
import oro.tirotiro.equipmentwarehouse.booking.persistence.BookingStatus;
import oro.tirotiro.equipmentwarehouse.config.AppProperties;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentCategory;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentItem;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.TrackingMode;

class CalendarServiceTests {

    @Test
    void buildsCurrentMonthTilesWithBookingCountsPerDay() {
        User actor = user("User");
        BookingService bookingService = mock(BookingService.class);
        when(bookingService.findOverlappingBookings(eq(actor), eq(BookingFilter.EMPTY), any(), any()))
                .thenReturn(List.of(
                        booking("2026-06-02T10:00:00Z", "2026-06-02T12:00:00Z"),
                        booking("2026-06-05T09:00:00Z", "2026-06-07T18:00:00Z")));
        CalendarService calendarService = new CalendarService(
                bookingService,
                Clock.fixed(Instant.parse("2026-06-15T08:00:00Z"), ZoneOffset.UTC),
                appProperties());

        CalendarService.MonthCalendar calendar = calendarService.monthCalendar(null, actor, BookingFilter.EMPTY);

        assertThat(calendar.month()).isEqualTo(YearMonth.parse("2026-06"));
        assertThat(calendar.days()).hasSize(30);
        assertThat(calendar.days().getFirst().date()).isEqualTo(LocalDate.parse("2026-06-01"));
        assertThat(calendar.days())
                .filteredOn(day -> day.date().equals(LocalDate.parse("2026-06-02")))
                .singleElement()
                .satisfies(day -> {
                    assertThat(day.bookingCount()).isEqualTo(1);
                    assertThat(day.hasBookings()).isTrue();
                });
        assertThat(calendar.days())
                .filteredOn(day -> day.date().equals(LocalDate.parse("2026-06-06")))
                .singleElement()
                .extracting(CalendarService.CalendarDay::bookingCount)
                .isEqualTo(1);
        assertThat(calendar.days())
                .filteredOn(day -> day.date().equals(LocalDate.parse("2026-06-08")))
                .singleElement()
                .extracting(CalendarService.CalendarDay::bookingCount)
                .isEqualTo(0);
    }

    @Test
    void buildsDayDetailWithBookingLines() {
        EquipmentItem camera = item("Камеры", "Sony FX6", TrackingMode.UNIT);
        User user = user("Ирина Продюсер");
        Booking booking = new Booking(
                user,
                Instant.parse("2026-06-15T10:00:00Z"),
                Instant.parse("2026-06-15T12:00:00Z"),
                BookingStatus.BOOKED);
        booking.setComment("Интервью");
        booking.replaceLines(java.util.Set.of(new BookingLine(booking, camera, null, 1)));
        BookingService bookingService = mock(BookingService.class);
        when(bookingService.findOverlappingBookings(eq(user), eq(BookingFilter.EMPTY), any(), any()))
                .thenReturn(List.of(booking));
        CalendarService calendarService = new CalendarService(
                bookingService,
                Clock.fixed(Instant.parse("2026-06-15T08:00:00Z"), ZoneOffset.UTC),
                appProperties());

        CalendarService.DayCalendar day = calendarService.dayCalendar(LocalDate.parse("2026-06-15"), user, BookingFilter.EMPTY);

        assertThat(day.date()).isEqualTo(LocalDate.parse("2026-06-15"));
        assertThat(day.filter()).isEqualTo(BookingFilter.EMPTY);
        assertThat(day.bookings()).singleElement().satisfies(summary -> {
            assertThat(summary.userDisplayName()).isEqualTo("Ирина Продюсер");
            assertThat(summary.comment()).isEqualTo("Интервью");
            assertThat(summary.lines()).singleElement().satisfies(line -> {
                assertThat(line.categoryName()).isEqualTo("Камеры");
                assertThat(line.equipmentName()).isEqualTo("Sony FX6");
                assertThat(line.quantity()).isEqualTo(1);
            });
        });
        verify(bookingService).findOverlappingBookings(eq(user), eq(BookingFilter.EMPTY), any(), any());
    }

    private Booking booking(String startsAt, String endsAt) {
        Booking booking = new Booking(user("User"), Instant.parse(startsAt), Instant.parse(endsAt), BookingStatus.BOOKED);
        ReflectionTestUtils.setField(booking, "id", UUID.randomUUID());
        return booking;
    }

    private User user(String displayName) {
        User user = new User(UUID.randomUUID() + "@example.com", "hash", displayName);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    private EquipmentItem item(String categoryName, String itemName, TrackingMode trackingMode) {
        EquipmentCategory category = new EquipmentCategory(categoryName, null);
        ReflectionTestUtils.setField(category, "id", UUID.randomUUID());
        EquipmentItem item = new EquipmentItem(category, itemName, trackingMode, trackingMode == TrackingMode.QUANTITY ? 3 : 0);
        ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
        return item;
    }

    private AppProperties appProperties() {
        return new AppProperties(
                ZoneId.of("UTC"),
                new AppProperties.Security(false),
                new AppProperties.BootstrapAdmin(null, null, null));
    }
}
