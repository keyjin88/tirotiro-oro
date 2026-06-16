package oro.tirotiro.equipmentwarehouse.web;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;

import oro.tirotiro.equipmentwarehouse.auth.CurrentUserService;
import oro.tirotiro.equipmentwarehouse.auth.persistence.User;
import oro.tirotiro.equipmentwarehouse.booking.BookingFilter;
import oro.tirotiro.equipmentwarehouse.booking.persistence.BookingStatus;
import oro.tirotiro.equipmentwarehouse.calendar.CalendarService;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentItemRepository;
import oro.tirotiro.equipmentwarehouse.auth.persistence.UserRepository;
import oro.tirotiro.equipmentwarehouse.permission.PermissionService;

class CalendarControllerTests {

    private final CalendarService calendarService = mock(CalendarService.class);
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final EquipmentItemRepository equipmentItemRepository = mock(EquipmentItemRepository.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final BookingFilterSupport bookingFilterSupport = new BookingFilterSupport(
            userRepository,
            equipmentItemRepository,
            permissionService);
    private final User actor = actor();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        when(currentUserService.requireCurrentUser()).thenReturn(actor);
        when(equipmentItemRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of());
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CalendarController(
                        calendarService,
                        currentUserService,
                        bookingFilterSupport,
                        Clock.fixed(Instant.parse("2026-06-15T08:00:00Z"), ZoneOffset.UTC)))
                .build();
    }

    @Test
    void rendersCalendarRouteForRequestedMonth() throws Exception {
        CalendarService.MonthCalendar calendar = monthCalendar(YearMonth.parse("2026-07"));
        when(calendarService.monthCalendar(YearMonth.parse("2026-07"), actor, BookingFilter.EMPTY)).thenReturn(calendar);

        mockMvc.perform(get("/calendar").param("month", "2026-07"))
                .andExpect(status().isOk())
                .andExpect(view().name("calendar/index"))
                .andExpect(model().attribute("selectedMonth", equalTo(YearMonth.parse("2026-07"))))
                .andExpect(model().attribute("calendar", equalTo(calendar)))
                .andExpect(model().attribute("bookingFilter", equalTo(BookingFilter.EMPTY)));

        verify(calendarService).monthCalendar(YearMonth.parse("2026-07"), actor, BookingFilter.EMPTY);
    }

    @Test
    void rendersMonthPartialForNavigationWithFilters() throws Exception {
        CalendarService.MonthCalendar calendar = monthCalendar(YearMonth.parse("2026-08"));
        UUID equipmentItemId = UUID.randomUUID();
        BookingFilter filter = BookingFilter.of(BookingStatus.BOOKED, null, null, null, equipmentItemId);
        when(calendarService.monthCalendar(YearMonth.parse("2026-08"), actor, filter)).thenReturn(calendar);

        mockMvc.perform(get("/calendar/partial")
                        .param("month", "2026-08")
                        .param("status", "BOOKED")
                        .param("equipmentItemId", equipmentItemId.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("calendar/partials/grid :: calendarGrid"))
                .andExpect(model().attribute("calendar", equalTo(calendar)))
                .andExpect(model().attribute("bookingFilter", equalTo(filter)));

        verify(calendarService).monthCalendar(YearMonth.parse("2026-08"), actor, filter);
    }

    @Test
    void rendersStableDayDetailRouteWithFilters() throws Exception {
        BookingFilter filter = BookingFilter.of(BookingStatus.CANCELLED, null, null, null, null);
        CalendarService.DayCalendar day = new CalendarService.DayCalendar(
                LocalDate.parse("2026-06-15"),
                YearMonth.parse("2026-06"),
                List.of(),
                filter);
        when(calendarService.dayCalendar(LocalDate.parse("2026-06-15"), actor, filter)).thenReturn(day);

        mockMvc.perform(get("/calendar/day/2026-06-15").param("status", "CANCELLED"))
                .andExpect(status().isOk())
                .andExpect(view().name("calendar/day"))
                .andExpect(model().attribute("day", equalTo(day)));

        verify(calendarService).dayCalendar(LocalDate.parse("2026-06-15"), actor, filter);
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

    private User actor() {
        User user = new User("user@example.com", "hash", "User");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }
}
