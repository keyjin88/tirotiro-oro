package oro.tirotiro.equipmentwarehouse.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;

import oro.tirotiro.equipmentwarehouse.auth.CurrentUserService;
import oro.tirotiro.equipmentwarehouse.auth.SecurityConfig;
import oro.tirotiro.equipmentwarehouse.auth.UserAdministrationService;
import oro.tirotiro.equipmentwarehouse.auth.UserRegistrationService;
import oro.tirotiro.equipmentwarehouse.auth.persistence.RoleCode;
import oro.tirotiro.equipmentwarehouse.auth.persistence.User;
import oro.tirotiro.equipmentwarehouse.auth.persistence.UserRepository;
import oro.tirotiro.equipmentwarehouse.booking.AvailabilityService;
import oro.tirotiro.equipmentwarehouse.booking.BookingService;
import oro.tirotiro.equipmentwarehouse.calendar.CalendarService;
import oro.tirotiro.equipmentwarehouse.config.AppProperties;
import oro.tirotiro.equipmentwarehouse.config.LocaleConfig;
import oro.tirotiro.equipmentwarehouse.inventory.InventoryService;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentCategory;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentCategoryRepository;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentItem;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentItemRepository;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentUnit;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentUnitRepository;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentUnitStatus;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.TrackingMode;
import oro.tirotiro.equipmentwarehouse.permission.PermissionService;
import oro.tirotiro.equipmentwarehouse.permission.persistence.PermissionCode;
import oro.tirotiro.equipmentwarehouse.permission.persistence.PermissionRepository;

@WebMvcTest(controllers = {
        AdminController.class,
        AuthController.class,
        BookingController.class,
        CalendarController.class,
        EquipmentController.class,
        HomeController.class
})
@Import({SecurityConfig.class, UiControllerAdvice.class, LocaleConfig.class, EquipmentWarehouseMvcTests.TestConfig.class})
class EquipmentWarehouseMvcTests {

    private static final Instant NOW = Instant.parse("2026-06-15T06:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private InventoryService inventoryService;

    @MockitoBean
    private AvailabilityService availabilityService;

    @MockitoBean
    private BookingService bookingService;

    @MockitoBean
    private CalendarService calendarService;

    @MockitoBean
    private PermissionService permissionService;

    @MockitoBean
    private UserRegistrationService userRegistrationService;

    @MockitoBean
    private UserAdministrationService userAdministrationService;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private EquipmentCategoryRepository categoryRepository;

    @MockitoBean
    private EquipmentItemRepository itemRepository;

    @MockitoBean
    private EquipmentUnitRepository unitRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private PermissionRepository permissionRepository;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void loginPageIsPublic() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Вход")));
    }

    @Test
    void registrationPageIsPublic() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Регистрация")));
    }

    @Test
    void registrationSuccessRedirectsToLogin() throws Exception {
        mockMvc.perform(post("/register")
                .with(csrf())
                .param("email", "viewer@example.com")
                .param("displayName", "Viewer")
                .param("password", "password123")
                .param("passwordConfirmation", "password123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verify(userRegistrationService).register(any(UserRegistrationService.RegisterUserCommand.class));
    }

    @Test
    void registrationValidationRerendersFormForMismatchAndDuplicateEmail() throws Exception {
        mockMvc.perform(post("/register")
                .with(csrf())
                .param("email", "viewer@example.com")
                .param("displayName", "Viewer")
                .param("password", "password123")
                .param("passwordConfirmation", "different"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Пароли не совпадают")));

        doThrow(new UserRegistrationService.DuplicateEmailException("Пользователь с такой почтой уже зарегистрирован."))
                .when(userRegistrationService)
                .register(any(UserRegistrationService.RegisterUserCommand.class));

        mockMvc.perform(post("/register")
                .with(csrf())
                .param("email", "viewer@example.com")
                .param("displayName", "Viewer")
                .param("password", "password123")
                .param("passwordConfirmation", "password123"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("уже зарегистрирован")));
    }

    @Test
    void formLoginSuccessRedirectsToEquipment() throws Exception {
        givenLoginUser();

        mockMvc.perform(post("/login")
                .with(csrf())
                .param("username", "operator@example.com")
                .param("password", "password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/equipment"));
    }

    @Test
    void formLoginSuccessIgnoresErrorSavedRequest() throws Exception {
        MockHttpSession session = (MockHttpSession) mockMvc.perform(get("/error"))
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getRequest()
                .getSession(false);
        assertThat(session).isNotNull();
        givenLoginUser();

        mockMvc.perform(post("/login")
                .session(session)
                .with(csrf())
                .param("username", "operator@example.com")
                .param("password", "password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/equipment"));
    }

    @Test
    void adminEquipmentRequiresAdminRole() throws Exception {
        mockMvc.perform(get("/admin/equipment").with(user("operator").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminUserMutationsRequireAdminRole() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/admin/users/{id}/roles", userId)
                .with(user("operator").roles("USER"))
                .with(csrf())
                .param("roleCode", RoleCode.ADMIN.name())
                .param("action", "grant"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/users/{id}/permissions", userId)
                .with(user("operator").roles("USER"))
                .with(csrf())
                .param("permissionCode", PermissionCode.EQUIPMENT_CREATE.name())
                .param("action", "grant"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminRoleMutationDelegatesToUserAdministrationService() throws Exception {
        UUID userId = UUID.randomUUID();
        User actor = actor();
        when(currentUserService.requireCurrentUser()).thenReturn(actor);

        mockMvc.perform(post("/admin/users/{id}/roles", userId)
                .with(user("admin").roles("ADMIN"))
                .with(csrf())
                .param("roleCode", RoleCode.ADMIN.name())
                .param("action", "grant"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));

        verify(userAdministrationService).grantRole(userId, RoleCode.ADMIN, actor);
    }

    @Test
    void equipmentCreationPageAllowsEquipmentCreateAuthority() throws Exception {
        EquipmentCategory category = category("Cameras");
        when(categoryRepository.findAll()).thenReturn(List.of(category));

        mockMvc.perform(get("/equipment/new").with(user("creator").authorities(() -> "EQUIPMENT_CREATE")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Добавить оборудование")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Cameras")));
    }

    @Test
    void equipmentMutationRequiresCsrfToken() throws Exception {
        UUID categoryId = UUID.randomUUID();

        mockMvc.perform(post("/equipment")
                .with(user("creator").authorities(() -> "EQUIPMENT_CREATE"))
                .param("categoryId", categoryId.toString())
                .param("name", "Tripod")
                .param("trackingMode", TrackingMode.QUANTITY.name())
                .param("totalQuantity", "2"))
                .andExpect(status().isForbidden());
    }

    @Test
    void catalogPageRendersEquipmentTable() throws Exception {
        EquipmentItem item = item("Camera", TrackingMode.QUANTITY, 3);
        when(inventoryService.findActiveCatalog()).thenReturn(List.of(item));

        mockMvc.perform(get("/equipment").with(user("viewer").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Каталог оборудования")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Camera")));
    }

    @Test
    void bookingFormSmokeRendersDefaultsWithoutLoadingCatalogItems() throws Exception {
        mockMvc.perform(get("/bookings/new").with(user("viewer").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Новое бронирование")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Каталог загружается по мере поиска")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-booking-starts-at")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-booking-ends-at")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/webjars/htmx.org/2.0.4/dist/htmx.min.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hx-include=\"this\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/bookings/equipment-search?lineIndex=0")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hx-target=\"#booking-line-0-search-results\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hx-trigger=\"input changed delay:300ms, search\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Введите минимум 2 символа для поиска.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("T23:59")));

        verify(inventoryService, never()).findActiveCatalog();
    }

    @Test
    void htmxWebJarAssetIsServedFromRenderedPath() throws Exception {
        mockMvc.perform(get("/webjars/htmx.org/2.0.4/dist/htmx.min.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("htmx")));
    }

    @Test
    void bookingLineRendersSearchHtmxWiringAndMinimumPrompt() throws Exception {
        mockMvc.perform(get("/bookings/line")
                .with(user("viewer").roles("USER"))
                .param("index", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"q\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/bookings/equipment-search?lineIndex=1")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hx-target=\"#booking-line-1-search-results\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hx-include=\"this\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hx-trigger=\"input changed delay:300ms, search\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Введите минимум 2 символа для поиска.")));
    }

    @Test
    void bookingCreatePostsMultiLineCommandWithCsrf() throws Exception {
        UUID quantityItemId = UUID.randomUUID();
        UUID unitItemId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        User actor = actor();
        when(currentUserService.requireCurrentUser()).thenReturn(actor);

        mockMvc.perform(post("/bookings")
                .with(user("viewer").roles("USER"))
                .with(csrf())
                .param("startsAt", "2026-06-15T09:00")
                .param("endsAt", "2026-06-15T10:00")
                .param("lines[0].equipmentItemId", quantityItemId.toString())
                .param("lines[0].quantity", "2")
                .param("lines[1].equipmentItemId", unitItemId.toString())
                .param("lines[1].equipmentUnitId", unitId.toString())
                .param("lines[1].quantity", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/bookings"));

        ArgumentCaptor<BookingService.CreateBookingCommand> command =
                ArgumentCaptor.forClass(BookingService.CreateBookingCommand.class);
        verify(bookingService).createBooking(command.capture(), eq(actor));
        assertThat(command.getValue().lines()).hasSize(2);
        assertThat(command.getValue().lines().get(0).equipmentItemId()).isEqualTo(quantityItemId);
        assertThat(command.getValue().lines().get(0).quantity()).isEqualTo(2);
        assertThat(command.getValue().lines().get(1).equipmentItemId()).isEqualTo(unitItemId);
        assertThat(command.getValue().lines().get(1).equipmentUnitId()).isEqualTo(unitId);
    }

    @Test
    void bookingEquipmentSearchReturnsLimitedMatchingItems() throws Exception {
        EquipmentItem item = item("Lens", TrackingMode.QUANTITY, 2);
        EquipmentItem russianItem = item("Камера", TrackingMode.UNIT, 0);
        when(itemRepository.searchActiveForBooking("Lens", PageRequest.of(0, 20))).thenReturn(List.of(item));
        when(itemRepository.searchActiveForBooking("Кам", PageRequest.of(0, 20))).thenReturn(List.of(russianItem));

        mockMvc.perform(get("/bookings/equipment-search")
                .with(user("viewer").roles("USER"))
                .param("lineIndex", "0")
                .param("q", "Lens"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Lens")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/bookings/equipment-selection")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hx-target=\"#booking-line-0\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("search-result")));

        mockMvc.perform(get("/bookings/equipment-search")
                .with(user("viewer").roles("USER"))
                .param("lineIndex", "0")
                .param("q", "Кам"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Камера")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("search-result")));
    }

    @Test
    void bookingEquipmentSearchShowsMinimumLengthPromptWithoutQuerying() throws Exception {
        mockMvc.perform(get("/bookings/equipment-search")
                .with(user("viewer").roles("USER"))
                .param("lineIndex", "0")
                .param("q", "L"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Введите минимум 2 символа для поиска.")));

        verify(itemRepository, never()).searchActiveForBooking(any(), any());
    }

    @Test
    void bookingEquipmentSearchRendersNoResultsMessage() throws Exception {
        when(itemRepository.searchActiveForBooking("Unknown", PageRequest.of(0, 20))).thenReturn(List.of());

        mockMvc.perform(get("/bookings/equipment-search")
                .with(user("viewer").roles("USER"))
                .param("lineIndex", "0")
                .param("q", "Unknown"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ничего не найдено.")));
    }

    @Test
    void bookingUnitSelectionLoadsConcreteUnitIdentifiers() throws Exception {
        EquipmentItem item = item("Camera", TrackingMode.UNIT, 0);
        EquipmentUnit unit = unit(item, "CAM-001", EquipmentUnitStatus.AVAILABLE);
        when(itemRepository.findDetailedById(item.getId())).thenReturn(Optional.of(item));
        when(unitRepository.findByEquipmentItem_IdAndArchivedFalseOrderByInventoryNumberAsc(item.getId()))
                .thenReturn(List.of(unit));

        mockMvc.perform(get("/bookings/equipment-selection")
                .with(user("viewer").roles("USER"))
                .param("lineIndex", "0")
                .param("equipmentItemId", item.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Camera")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("CAM-001")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("booking-line-0")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"Camera\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("lines[0].equipmentUnitId")));
    }

    @Test
    void bookingLineRemovalRerendersCompactedSelectedLinesWithCsrf() throws Exception {
        EquipmentItem firstItem = item("Lens", TrackingMode.QUANTITY, 2);
        EquipmentItem removedItem = item("Light", TrackingMode.QUANTITY, 4);
        EquipmentItem lastItem = item("Camera", TrackingMode.UNIT, 0);
        EquipmentUnit lastUnit = unit(lastItem, "CAM-001", EquipmentUnitStatus.AVAILABLE);
        when(itemRepository.findDetailedById(firstItem.getId())).thenReturn(Optional.of(firstItem));
        when(itemRepository.findDetailedById(lastItem.getId())).thenReturn(Optional.of(lastItem));
        when(unitRepository.findByEquipmentItem_IdAndArchivedFalseOrderByInventoryNumberAsc(lastItem.getId()))
                .thenReturn(List.of(lastUnit));

        mockMvc.perform(post("/bookings/line/remove")
                .with(user("viewer").roles("USER"))
                .with(csrf())
                .param("startsAt", "2026-06-15T09:00")
                .param("endsAt", "2026-06-15T10:00")
                .param("removeIndex", "1")
                .param("lines[0].equipmentItemId", firstItem.getId().toString())
                .param("lines[0].quantity", "2")
                .param("lines[1].equipmentItemId", removedItem.getId().toString())
                .param("lines[1].quantity", "4")
                .param("lines[2].equipmentItemId", lastItem.getId().toString())
                .param("lines[2].equipmentUnitId", lastUnit.getId().toString())
                .param("lines[2].quantity", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Lens")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Camera")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("CAM-001")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("lines[0].equipmentItemId")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("lines[1].equipmentUnitId")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Light"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("lines[2]"))));
    }

    @Test
    void adminEquipmentCreatePostsCommandWithCsrf() throws Exception {
        UUID categoryId = UUID.randomUUID();
        User actor = actor();
        when(currentUserService.requireCurrentUser()).thenReturn(actor);

        mockMvc.perform(post("/admin/equipment")
                .with(user("admin").roles("ADMIN"))
                .with(csrf())
                .param("categoryId", categoryId.toString())
                .param("name", "Monitor")
                .param("trackingMode", TrackingMode.QUANTITY.name())
                .param("totalQuantity", "4"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/equipment"));

        verify(inventoryService).createItem(any(InventoryService.CreateItemCommand.class), eq(actor));
    }

    @Test
    void calendarPartialRendersSelectedMonthTilesAndBookingCounts() throws Exception {
        YearMonth month = YearMonth.parse("2026-06");
        CalendarService.MonthCalendar view = new CalendarService.MonthCalendar(
                month,
                month.minusMonths(1),
                month.plusMonths(1),
                month.atDay(1),
                month.atEndOfMonth(),
                List.of(new CalendarService.CalendarDay(LocalDate.parse("2026-06-15"), "пн", 2)));
        when(calendarService.monthCalendar(month)).thenReturn(view);

        mockMvc.perform(get("/calendar/partial")
                .with(user("viewer").roles("USER"))
                .param("month", "2026-06"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Бронирований: 2")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/calendar/day/2026-06-15")));
    }

    private EquipmentCategory category(String name) {
        EquipmentCategory category = new EquipmentCategory(name, null);
        ReflectionTestUtils.setField(category, "id", UUID.randomUUID());
        return category;
    }

    private EquipmentItem item(String name, TrackingMode trackingMode, int totalQuantity) {
        EquipmentItem item = new EquipmentItem(category("Production"), name, trackingMode, totalQuantity);
        ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
        return item;
    }

    private EquipmentUnit unit(EquipmentItem item, String inventoryNumber, EquipmentUnitStatus status) {
        EquipmentUnit unit = new EquipmentUnit(item, inventoryNumber, "Готово", status);
        ReflectionTestUtils.setField(unit, "id", UUID.randomUUID());
        return unit;
    }

    private User actor() {
        User user = new User("actor@example.com", "hash", "Actor");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    private void givenLoginUser() {
        when(userDetailsService.loadUserByUsername("operator@example.com"))
                .thenReturn(org.springframework.security.core.userdetails.User
                        .withUsername("operator@example.com")
                        .password(passwordEncoder.encode("password"))
                        .roles("USER")
                        .build());
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
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
