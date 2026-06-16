package oro.tirotiro.equipmentwarehouse.web;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import oro.tirotiro.equipmentwarehouse.auth.CurrentUserService;
import oro.tirotiro.equipmentwarehouse.booking.BookingFilter;
import oro.tirotiro.equipmentwarehouse.booking.persistence.BookingStatus;
import oro.tirotiro.equipmentwarehouse.calendar.CalendarService;

@Controller
@RequestMapping("/calendar")
public class CalendarController {

    private final CalendarService calendarService;
    private final CurrentUserService currentUserService;
    private final BookingFilterSupport bookingFilterSupport;
    private final Clock clock;

    public CalendarController(
            CalendarService calendarService,
            CurrentUserService currentUserService,
            BookingFilterSupport bookingFilterSupport,
            Clock clock) {
        this.calendarService = calendarService;
        this.currentUserService = currentUserService;
        this.bookingFilterSupport = bookingFilterSupport;
        this.clock = clock;
    }

    @GetMapping
    public String calendar(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) UUID equipmentItemId,
            Model model) {
        BookingFilter filter = bookingFilterSupport.parse(status, userId, fromDate, toDate, equipmentItemId);
        addCalendarModel(model, month, filter);
        return "calendar/index";
    }

    @GetMapping("/partial")
    public String calendarPartial(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) UUID equipmentItemId,
            Model model) {
        BookingFilter filter = bookingFilterSupport.parse(status, userId, fromDate, toDate, equipmentItemId);
        addCalendarModel(model, month, filter);
        return "calendar/partials/grid :: calendarGrid";
    }

    @GetMapping("/day/{date}")
    public String calendarDay(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) UUID equipmentItemId,
            Model model) {
        BookingFilter filter = bookingFilterSupport.parse(status, userId, fromDate, toDate, equipmentItemId);
        var actor = currentUserService.requireCurrentUser();
        model.addAttribute("day", calendarService.dayCalendar(date, actor, filter));
        bookingFilterSupport.addFilterModel(model, actor, filter, true, YearMonth.from(date));
        return "calendar/day";
    }

    private void addCalendarModel(Model model, YearMonth month, BookingFilter filter) {
        YearMonth selectedMonth = month == null ? YearMonth.from(LocalDate.now(clock)) : month;
        var actor = currentUserService.requireCurrentUser();
        model.addAttribute("selectedMonth", selectedMonth);
        model.addAttribute("calendar", calendarService.monthCalendar(selectedMonth, actor, filter));
        bookingFilterSupport.addFilterModel(model, actor, filter, true, selectedMonth);
    }
}
