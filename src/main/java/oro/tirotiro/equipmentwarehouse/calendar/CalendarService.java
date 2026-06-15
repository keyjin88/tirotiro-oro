package oro.tirotiro.equipmentwarehouse.calendar;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import oro.tirotiro.equipmentwarehouse.config.AppProperties;
import oro.tirotiro.equipmentwarehouse.booking.persistence.Booking;
import oro.tirotiro.equipmentwarehouse.booking.persistence.BookingRepository;
import oro.tirotiro.equipmentwarehouse.booking.persistence.BookingStatus;

@Service
public class CalendarService {

    private final BookingRepository bookingRepository;
    private final Clock clock;
    private final ZoneId zoneId;

    public CalendarService(
            BookingRepository bookingRepository,
            Clock clock,
            AppProperties appProperties) {
        this.bookingRepository = bookingRepository;
        this.clock = clock;
        this.zoneId = appProperties.timeZone();
    }

    @Transactional(readOnly = true)
    public MonthCalendar monthCalendar(YearMonth month) {
        YearMonth selectedMonth = month == null ? YearMonth.now(clock.withZone(zoneId)) : month;
        LocalDate firstDay = selectedMonth.atDay(1);
        LocalDate lastDay = selectedMonth.atEndOfMonth();
        List<Booking> bookings = bookingsOverlapping(firstDay, lastDay.plusDays(1));
        Map<LocalDate, Integer> bookingCountByDate = bookingCountsByDate(firstDay, lastDay, bookings);

        List<CalendarDay> days = firstDay.datesUntil(lastDay.plusDays(1))
                .map(date -> new CalendarDay(
                        date,
                        date.getDayOfWeek().getDisplayName(TextStyle.SHORT_STANDALONE, Locale.forLanguageTag("ru")),
                        bookingCountByDate.getOrDefault(date, 0)))
                .toList();
        return new MonthCalendar(
                selectedMonth,
                selectedMonth.minusMonths(1),
                selectedMonth.plusMonths(1),
                firstDay,
                lastDay,
                days);
    }

    @Transactional(readOnly = true)
    public DayCalendar dayCalendar(LocalDate date) {
        LocalDate selectedDate = date == null ? LocalDate.now(clock.withZone(zoneId)) : date;
        List<BookingSummary> bookings = bookingsOverlapping(selectedDate, selectedDate.plusDays(1)).stream()
                .map(this::toBookingSummary)
                .toList();
        return new DayCalendar(selectedDate, YearMonth.from(selectedDate), bookings);
    }

    private List<Booking> bookingsOverlapping(LocalDate startsOn, LocalDate endsOn) {
        return bookingRepository.findByStatusAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAtAsc(
                BookingStatus.BOOKED,
                endsOn.atStartOfDay(zoneId).toInstant(),
                startsOn.atStartOfDay(zoneId).toInstant());
    }

    private Map<LocalDate, Integer> bookingCountsByDate(LocalDate firstDay, LocalDate lastDay, List<Booking> bookings) {
        Map<LocalDate, Integer> counts = new LinkedHashMap<>();
        firstDay.datesUntil(lastDay.plusDays(1)).forEach(date -> counts.put(date, 0));
        for (Booking booking : bookings) {
            LocalDate bookingStart = LocalDate.ofInstant(booking.getStartsAt(), zoneId);
            LocalDate bookingEnd = LocalDate.ofInstant(booking.getEndsAt().minusMillis(1), zoneId);
            LocalDate startsOn = bookingStart.isBefore(firstDay) ? firstDay : bookingStart;
            LocalDate endsOn = bookingEnd.isAfter(lastDay) ? lastDay : bookingEnd;
            startsOn.datesUntil(endsOn.plusDays(1))
                    .forEach(date -> counts.computeIfPresent(date, (ignored, count) -> count + 1));
        }
        return counts;
    }

    private BookingSummary toBookingSummary(Booking booking) {
        List<BookingLineSummary> lines = new ArrayList<>(booking.getLines().stream()
                .map(line -> new BookingLineSummary(
                        line.getEquipmentItem().getCategory().getName(),
                        line.getEquipmentItem().getName(),
                        line.getEquipmentUnit() == null ? null : line.getEquipmentUnit().getInventoryNumber(),
                        line.getQuantity()))
                .toList());
        lines.sort(Comparator
                .comparing(BookingLineSummary::equipmentName)
                .thenComparing(line -> line.inventoryNumber() == null ? "" : line.inventoryNumber()));
        return new BookingSummary(
                booking.getId(),
                booking.getStartsAt(),
                booking.getEndsAt(),
                booking.getUser().getDisplayName(),
                booking.getComment(),
                lines);
    }

    public record MonthCalendar(
            YearMonth month,
            YearMonth previousMonth,
            YearMonth nextMonth,
            LocalDate firstDay,
            LocalDate lastDay,
            List<CalendarDay> days) {
    }

    public record CalendarDay(LocalDate date, String weekdayName, int bookingCount) {

        public boolean hasBookings() {
            return bookingCount > 0;
        }
    }

    public record DayCalendar(LocalDate date, YearMonth month, List<BookingSummary> bookings) {
    }

    public record BookingSummary(
            UUID id,
            java.time.Instant startsAt,
            java.time.Instant endsAt,
            String userDisplayName,
            String comment,
            List<BookingLineSummary> lines) {
    }

    public record BookingLineSummary(
            String categoryName,
            String equipmentName,
            String inventoryNumber,
            int quantity) {
    }
}
