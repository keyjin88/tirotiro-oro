package oro.tirotiro.equipmentwarehouse.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import oro.tirotiro.equipmentwarehouse.auth.CurrentUserService;
import oro.tirotiro.equipmentwarehouse.auth.SecurityConfig;
import oro.tirotiro.equipmentwarehouse.auth.UserAdministrationService;
import oro.tirotiro.equipmentwarehouse.auth.UserRegistrationService;
import oro.tirotiro.equipmentwarehouse.auth.persistence.User;
import oro.tirotiro.equipmentwarehouse.auth.persistence.UserRepository;
import oro.tirotiro.equipmentwarehouse.booking.AvailabilityService;
import oro.tirotiro.equipmentwarehouse.booking.BookingService;
import oro.tirotiro.equipmentwarehouse.booking.persistence.Booking;
import oro.tirotiro.equipmentwarehouse.booking.persistence.BookingLine;
import oro.tirotiro.equipmentwarehouse.booking.persistence.BookingStatus;
import oro.tirotiro.equipmentwarehouse.calendar.CalendarService;
import oro.tirotiro.equipmentwarehouse.config.AppProperties;
import oro.tirotiro.equipmentwarehouse.config.LocaleConfig;
import oro.tirotiro.equipmentwarehouse.inventory.InventoryService;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentCategory;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentCategoryRepository;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentItem;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentItemRepository;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentUnitRepository;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.TrackingMode;
import oro.tirotiro.equipmentwarehouse.permission.PermissionService;
import oro.tirotiro.equipmentwarehouse.permission.persistence.PermissionRepository;

@WebMvcTest(controllers = {AdminController.class, BookingController.class})
@Import({SecurityConfig.class, UiControllerAdvice.class, LocaleConfig.class, BookingFilterSupport.class, AdminPageRenderProbeTest.TestConfig.class})
class AdminPageRenderProbeTest {

    private static final Instant START = Instant.parse("2026-06-15T10:00:00Z");
    private static final Instant END = Instant.parse("2026-06-15T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private InventoryService inventoryService;
    @MockitoBean private AvailabilityService availabilityService;
    @MockitoBean private BookingService bookingService;
    @MockitoBean private CalendarService calendarService;
    @MockitoBean private PermissionService permissionService;
    @MockitoBean private UserRegistrationService userRegistrationService;
    @MockitoBean private UserAdministrationService userAdministrationService;
    @MockitoBean private CurrentUserService currentUserService;
    @MockitoBean private EquipmentCategoryRepository categoryRepository;
    @MockitoBean private EquipmentItemRepository itemRepository;
    @MockitoBean private EquipmentUnitRepository unitRepository;
    @MockitoBean private UserRepository userRepository;
    @MockitoBean private PermissionRepository permissionRepository;
    @MockitoBean private UserDetailsService userDetailsService;

    @Test
    void adminEquipmentPageRendersWithActiveItemsForAdmin() throws Exception {
        EquipmentCategory category = new EquipmentCategory("Cameras", "Video");
        ReflectionTestUtils.setField(category, "id", UUID.randomUUID());
        EquipmentItem item = new EquipmentItem(category, "Sony FX3", TrackingMode.QUANTITY, 2);
        ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
        when(categoryRepository.findAll()).thenReturn(List.of(category));
        when(itemRepository.findAllDetailed()).thenReturn(List.of(item));

        mockMvc.perform(get("/admin/equipment").with(user("admin").roles("ADMIN")))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    void adminUsersPageRendersWithUsersForAdmin() throws Exception {
        User user = new User("u@example.com", "hash", "User One");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        when(userRepository.findAllWithRolesAndPermissions()).thenReturn(List.of(user));
        when(permissionRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/admin/users").with(user("admin").roles("ADMIN")))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    void bookingsPageRendersWithBookingsForAdmin() throws Exception {
        User actor = new User("admin@example.com", "hash", "Local Admin");
        ReflectionTestUtils.setField(actor, "id", UUID.randomUUID());
        EquipmentCategory category = new EquipmentCategory("Cameras", "Video");
        ReflectionTestUtils.setField(category, "id", UUID.randomUUID());
        EquipmentItem item = new EquipmentItem(category, "Sony FX3", TrackingMode.QUANTITY, 2);
        ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
        Booking booking = new Booking(actor, START, END, BookingStatus.BOOKED);
        ReflectionTestUtils.setField(booking, "id", UUID.randomUUID());
        booking.replaceLines(Set.of(new BookingLine(booking, item, null, 1)));
        when(currentUserService.requireCurrentUser()).thenReturn(actor);
        when(bookingService.findBookings(eq(actor), any())).thenReturn(List.of(booking));
        when(permissionService.isAdmin(actor)).thenReturn(true);
        when(userRepository.findAllByOrderByDisplayNameAsc()).thenReturn(List.of(actor));
        when(itemRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(item));

        mockMvc.perform(get("/bookings").with(user("admin").roles("ADMIN")))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        AppProperties appProperties() {
            return new AppProperties(
                    ZoneId.of("UTC"),
                    new AppProperties.Security(false),
                    new AppProperties.BootstrapAdmin(null, null, null));
        }

        @Bean
        Clock clock() {
            return Clock.fixed(START, ZoneOffset.UTC);
        }
    }
}
