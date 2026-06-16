package oro.tirotiro.equipmentwarehouse.web;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import oro.tirotiro.equipmentwarehouse.calendar.CalendarService;

@Controller
@RequestMapping("/calendar")
public class CalendarController {

    private final CalendarService calendarService;
    private final Clock clock;

    public CalendarController(
            CalendarService calendarService,
            Clock clock) {
        this.calendarService = calendarService;
        this.clock = clock;
    }

    @GetMapping
    public String calendar(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            Model model) {
        addCalendarModel(model, month);
        return "calendar/index";
    }

    @GetMapping("/partial")
    public String calendarPartial(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            Model model) {
        addCalendarModel(model, month);
        return "calendar/partials/grid :: calendarGrid";
    }

    @GetMapping("/day/{date}")
    public String calendarDay(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Model model) {
        model.addAttribute("day", calendarService.dayCalendar(date));
        return "calendar/day";
    }

    private void addCalendarModel(Model model, YearMonth month) {
        YearMonth selectedMonth = month == null ? YearMonth.from(LocalDate.now(clock)) : month;
        model.addAttribute("selectedMonth", selectedMonth);
        model.addAttribute("calendar", calendarService.monthCalendar(selectedMonth));
    }
}
