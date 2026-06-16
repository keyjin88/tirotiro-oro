package oro.tirotiro.equipmentwarehouse.web;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import oro.tirotiro.equipmentwarehouse.calendar.CalendarService;

class CalendarControllerTests {

    private final CalendarService calendarService = mock(CalendarService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new CalendarController(
                    calendarService,
                    Clock.fixed(Instant.parse("2026-06-15T08:00:00Z"), ZoneOffset.UTC)))
            .build();

    @Test
    void rendersCalendarRouteForRequestedMonth() throws Exception {
        CalendarService.MonthCalendar calendar = monthCalendar(YearMonth.parse("2026-07"));
        when(calendarService.monthCalendar(YearMonth.parse("2026-07"))).thenReturn(calendar);

        mockMvc.perform(get("/calendar").param("month", "2026-07"))
                .andExpect(status().isOk())
                .andExpect(view().name("calendar/index"))
                .andExpect(model().attribute("selectedMonth", equalTo(YearMonth.parse("2026-07"))))
                .andExpect(model().attribute("calendar", equalTo(calendar)));

        verify(calendarService).monthCalendar(YearMonth.parse("2026-07"));
    }

    @Test
    void rendersMonthPartialForNavigation() throws Exception {
        CalendarService.MonthCalendar calendar = monthCalendar(YearMonth.parse("2026-08"));
        when(calendarService.monthCalendar(YearMonth.parse("2026-08"))).thenReturn(calendar);

        mockMvc.perform(get("/calendar/partial").param("month", "2026-08"))
                .andExpect(status().isOk())
                .andExpect(view().name("calendar/partials/grid :: calendarGrid"))
                .andExpect(model().attribute("calendar", equalTo(calendar)));

        verify(calendarService).monthCalendar(YearMonth.parse("2026-08"));
    }

    @Test
    void rendersStableDayDetailRoute() throws Exception {
        CalendarService.DayCalendar day = new CalendarService.DayCalendar(
                LocalDate.parse("2026-06-15"),
                YearMonth.parse("2026-06"),
                List.of());
        when(calendarService.dayCalendar(LocalDate.parse("2026-06-15"))).thenReturn(day);

        mockMvc.perform(get("/calendar/day/2026-06-15"))
                .andExpect(status().isOk())
                .andExpect(view().name("calendar/day"))
                .andExpect(model().attribute("day", equalTo(day)));

        verify(calendarService).dayCalendar(LocalDate.parse("2026-06-15"));
    }

    private CalendarService.MonthCalendar monthCalendar(YearMonth month) {
        return new CalendarService.MonthCalendar(
                month,
                month.minusMonths(1),
                month.plusMonths(1),
                month.atDay(1),
                month.atEndOfMonth(),
                List.of());
    }
}
