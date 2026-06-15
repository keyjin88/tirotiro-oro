package oro.tirotiro.equipmentwarehouse.web;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import oro.tirotiro.equipmentwarehouse.calendar.CalendarService;
import oro.tirotiro.equipmentwarehouse.config.AppProperties;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentCategoryRepository;

@Controller
@RequestMapping("/calendar")
public class CalendarController {

    private final CalendarService calendarService;
    private final EquipmentCategoryRepository categoryRepository;
    private final Clock clock;
    private final ZoneId zoneId;

    public CalendarController(
            CalendarService calendarService,
            EquipmentCategoryRepository categoryRepository,
            Clock clock,
            AppProperties appProperties) {
        this.calendarService = calendarService;
        this.categoryRepository = categoryRepository;
        this.clock = clock;
        this.zoneId = appProperties.timeZone();
    }

    @GetMapping
    public String calendar(Model model) {
        addCalendarModel(model, LocalDate.now(clock), LocalDate.now(clock).plusDays(7), null);
        return "calendar/index";
    }

    @GetMapping("/partial")
    public String calendarPartial(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startsOn,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endsOn,
            @RequestParam(required = false) UUID categoryId,
            Model model) {
        addCalendarModel(model, startsOn, endsOn, categoryId);
        return "calendar/partials/grid :: calendarGrid";
    }

    private void addCalendarModel(Model model, LocalDate startsOn, LocalDate endsOn, UUID categoryId) {
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("startsOn", startsOn);
        model.addAttribute("endsOn", endsOn);
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("calendar", calendarService.availabilityCalendar(
                startsOn.atStartOfDay(zoneId).toInstant(),
                endsOn.plusDays(1).atStartOfDay(zoneId).toInstant(),
                categoryId));
    }
}
