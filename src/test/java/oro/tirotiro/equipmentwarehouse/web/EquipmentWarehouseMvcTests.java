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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import oro.tirotiro.equipmentwarehouse.booking.BookingFilter;
import oro.tirotiro.equipmentwarehouse.booking.BookingService;
import oro.tirotiro.equipmentwarehouse.booking.persistence.Booking;
import oro.tirotiro.equipmentwarehouse.booking.persistence.BookingLine;
import oro.tirotiro.equipmentwarehouse.booking.persistence.BookingStatus;
import oro.tirotiro.equipmentwarehouse.calendar.CalendarService;
import oro.tirotiro.equipmentwarehouse.config.AppProperties;
import oro.tirotiro.equipmentwarehouse.config.LocaleConfig;
import oro.tirotiro.equipmentwarehouse.inventory.CatalogActiveFilter;
import oro.tirotiro.equipmentwarehouse.inventory.EquipmentCatalogFilter;
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
@Import({SecurityConfig.class, UiControllerAdvice.class, LocaleConfig.class, BookingFilterSupport.class, EquipmentCatalogFilterSupport.class, EquipmentWarehouseMvcTests.TestConfig.class})
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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Вход")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Версия 0.2.0-test")));
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

        mockMvc.perform(post("/admin/users/{id}/delete", userId)
                .with(user("operator").roles("USER"))
                .with(csrf()))
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
    void adminUserDeleteDelegatesToUserAdministrationService() throws Exception {
        UUID userId = UUID.randomUUID();
        User actor = actor();
        when(currentUserService.requireCurrentUser()).thenReturn(actor);

        mockMvc.perform(post("/admin/users/{id}/delete", userId)
                .with(user("admin").roles("ADMIN"))
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));

        verify(userAdministrationService).deleteUser(userId, actor);
    }

    @Test
    void adminUserDeleteRequiresCsrfToken() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/admin/users/{id}/delete", userId)
                .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());

        verify(userAdministrationService, never()).deleteUser(any(), any());
    }

    @Test
    void adminBookingDeleteDelegatesToBookingService() throws Exception {
        UUID bookingId = UUID.randomUUID();
        User actor = actor();
        when(currentUserService.requireCurrentUser()).thenReturn(actor);

        mockMvc.perform(post("/admin/bookings/{id}/delete", bookingId)
                .with(user("admin").roles("ADMIN"))
                .with(csrf())
                .param("reason", "duplicate entry"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/bookings"));

        verify(bookingService).deleteBooking(bookingId, "duplicate entry", actor);
    }

    @Test
    void adminBookingDeleteRequiresAdminRole() throws Exception {
        UUID bookingId = UUID.randomUUID();

        mockMvc.perform(post("/admin/bookings/{id}/delete", bookingId)
                .with(user("operator").roles("USER"))
                .with(csrf())
                .param("reason", "cleanup"))
                .andExpect(status().isForbidden());

        verify(bookingService, never()).deleteBooking(any(), any(), any());
    }

    @Test
    void adminBookingDeleteRequiresCsrfToken() throws Exception {
        UUID bookingId = UUID.randomUUID();

        mockMvc.perform(post("/admin/bookings/{id}/delete", bookingId)
                .with(user("admin").roles("ADMIN"))
                .param("reason", "cleanup"))
                .andExpect(status().isForbidden());

        verify(bookingService, never()).deleteBooking(any(), any(), any());
    }

    @Test
    void adminEquipmentDeleteRequiresCsrfToken() throws Exception {
        UUID itemId = UUID.randomUUID();

        mockMvc.perform(post("/admin/equipment/{id}/delete", itemId)
                .with(user("admin").roles("ADMIN"))
                .param("reason", "broken"))
                .andExpect(status().isForbidden());

        verify(inventoryService, never()).softDeleteItem(any(), any(), any());
    }

    @Test
    void adminEquipmentRestoreDelegatesToInventoryService() throws Exception {
        UUID itemId = UUID.randomUUID();
        User actor = actor();
        when(currentUserService.requireCurrentUser()).thenReturn(actor);

        mockMvc.perform(post("/admin/equipment/{id}/restore", itemId)
                .with(user("admin").roles("ADMIN"))
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/equipment"));

        verify(inventoryService).restoreItem(itemId, actor);
    }

    @Test
    void adminEquipmentRestoreRequiresCsrfToken() throws Exception {
        UUID itemId = UUID.randomUUID();

        mockMvc.perform(post("/admin/equipment/{id}/restore", itemId)
                .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());

        verify(inventoryService, never()).restoreItem(any(), any());
    }

    @Test
    void adminEquipmentRestoreRequiresAdminRole() throws Exception {
        UUID itemId = UUID.randomUUID();

        mockMvc.perform(post("/admin/equipment/{id}/restore", itemId)
                .with(user("operator").roles("USER"))
                .with(csrf()))
                .andExpect(status().isForbidden());

        verify(inventoryService, never()).restoreItem(any(), any());
    }

    @Test
    void adminEquipmentPermanentDeleteDelegatesToInventoryService() throws Exception {
        UUID itemId = UUID.randomUUID();
        User actor = actor();
        when(currentUserService.requireCurrentUser()).thenReturn(actor);

        mockMvc.perform(post("/admin/equipment/{id}/permanent-delete", itemId)
                .with(user("admin").roles("ADMIN"))
                .with(csrf())
                .param("reason", "duplicate entry"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/equipment"));

        verify(inventoryService).permanentlyDeleteItem(itemId, "duplicate entry", actor);
    }

    @Test
    void adminEquipmentPermanentDeleteRequiresCsrfToken() throws Exception {
        UUID itemId = UUID.randomUUID();

        mockMvc.perform(post("/admin/equipment/{id}/permanent-delete", itemId)
                .with(user("admin").roles("ADMIN"))
                .param("reason", "cleanup"))
                .andExpect(status().isForbidden());

        verify(inventoryService, never()).permanentlyDeleteItem(any(), any(), any());
    }

    @Test
    void adminEquipmentPermanentDeleteRequiresAdminRole() throws Exception {
        UUID itemId = UUID.randomUUID();

        mockMvc.perform(post("/admin/equipment/{id}/permanent-delete", itemId)
                .with(user("operator").roles("USER"))
                .with(csrf())
                .param("reason", "cleanup"))
                .andExpect(status().isForbidden());

        verify(inventoryService, never()).permanentlyDeleteItem(any(), any(), any());
    }

    @Test
    void equipmentCreationPageAllowsEquipmentCreateAuthority() throws Exception {
        User actor = actor();
        EquipmentCategory category = category("Cameras");
        when(currentUserService.requireCurrentUser()).thenReturn(actor);
        when(categoryRepository.findAll()).thenReturn(List.of(category));
        when(userRepository.findAllByEnabledTrueOrderByDisplayNameAsc()).thenReturn(List.of(actor));

        mockMvc.perform(get("/equipment/new").with(user("creator").authorities(() -> "EQUIPMENT_CREATE")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Добавить оборудование")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Cameras")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Владелец")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Actor")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("selected=\"selected\"")));
    }

    @Test
    void equipmentCreationPageRendersDistinctOwnerOptionValues() throws Exception {
        User actor = actor();
        User otherOwner = new User("other@example.com", "hash", "Other User");
        ReflectionTestUtils.setField(otherOwner, "id", UUID.fromString("11111111-1111-1111-1111-111111111111"));
        EquipmentCategory category = category("Cameras");
        when(currentUserService.requireCurrentUser()).thenReturn(actor);
        when(categoryRepository.findAll()).thenReturn(List.of(category));
        when(userRepository.findAllByEnabledTrueOrderByDisplayNameAsc()).thenReturn(List.of(actor, otherOwner));

        String html = mockMvc.perform(get("/equipment/new").with(user("creator").authorities(() -> "EQUIPMENT_CREATE")))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html).contains("value=\"" + actor.getId() + "\"");
        assertThat(html).contains("value=\"11111111-1111-1111-1111-111111111111\"");
        assertThat(html).contains("selected=\"selected\"");
    }

    @Test
    void equipmentCreatePostsOwnerUserId() throws Exception {
        UUID categoryId = UUID.randomUUID();
        User actor = actor();
        User otherOwner = new User("owner@example.com", "hash", "Owner User");
        ReflectionTestUtils.setField(otherOwner, "id", UUID.randomUUID());
        EquipmentItem created = item("Tripod", TrackingMode.QUANTITY, 2);
        created.assignOwner(otherOwner);
        when(currentUserService.requireCurrentUser()).thenReturn(actor);
        when(inventoryService.createItem(any(InventoryService.CreateItemCommand.class), eq(actor))).thenReturn(created);

        mockMvc.perform(post("/equipment")
                .with(user("creator").authorities(() -> "EQUIPMENT_CREATE"))
                .with(csrf())
                .param("categoryId", categoryId.toString())
                .param("name", "Tripod")
                .param("ownerUserId", otherOwner.getId().toString())
                .param("trackingMode", TrackingMode.QUANTITY.name())
                .param("totalQuantity", "2"))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<InventoryService.CreateItemCommand> commandCaptor =
                ArgumentCaptor.forClass(InventoryService.CreateItemCommand.class);
        verify(inventoryService).createItem(commandCaptor.capture(), eq(actor));
        assertThat(commandCaptor.getValue().ownerUserId()).isEqualTo(otherOwner.getId());
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
        User actor = actor();
        EquipmentItem item = item("Camera", TrackingMode.QUANTITY, 3);
        item.assignOwner(actor);
        when(currentUserService.requireCurrentUser()).thenReturn(actor);
        when(inventoryService.findCatalog(eq(EquipmentCatalogFilter.EMPTY), eq(actor))).thenReturn(List.of(item));
        when(categoryRepository.findAll()).thenReturn(List.of(category("Production")));

        mockMvc.perform(get("/equipment").with(user("viewer").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Каталог оборудования")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Camera")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Владелец")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Actor")));
    }

    @Test
    void catalogPageRendersFilterPanelAndAppliesFilters() throws Exception {
        User actor = actor();
        UUID categoryId = UUID.randomUUID();
        EquipmentCatalogFilter filter = EquipmentCatalogFilter.of("Lens", categoryId, TrackingMode.UNIT, CatalogActiveFilter.ACTIVE);
        when(currentUserService.requireCurrentUser()).thenReturn(actor);
        when(inventoryService.findCatalog(eq(filter), eq(actor))).thenReturn(List.of());
        when(categoryRepository.findAll()).thenReturn(List.of(category("Production")));

        mockMvc.perform(get("/equipment")
                .with(user("viewer").roles("USER"))
                .param("search", "Lens")
                .param("categoryId", categoryId.toString())
                .param("trackingMode", TrackingMode.UNIT.name()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Все категории")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Применить")));

        verify(inventoryService).findCatalog(eq(filter), eq(actor));
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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/booking-form.js")));

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
                .param("ownerUserId", actor.getId().toString())
                .param("trackingMode", TrackingMode.QUANTITY.name())
                .param("totalQuantity", "4"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/equipment"));

        verify(inventoryService).createItem(any(InventoryService.CreateItemCommand.class), eq(actor));
    }

    @Test
    void calendarPartialRendersSelectedMonthTilesAndBookingCounts() throws Exception {
        YearMonth month = YearMonth.parse("2026-06");
        User actor = actor();
        CalendarService.MonthCalendar view = new CalendarService.MonthCalendar(
                month,
                month.minusMonths(1),
                month.plusMonths(1),
                month.atDay(1),
                month.atEndOfMonth(),
                List.of(new CalendarService.CalendarDay(LocalDate.parse("2026-06-15"), "пн", 2)));
        when(currentUserService.requireCurrentUser()).thenReturn(actor);
        when(calendarService.monthCalendar(eq(month), eq(actor), eq(BookingFilter.EMPTY))).thenReturn(view);
        when(itemRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of());

        mockMvc.perform(get("/calendar/partial")
                .with(user("viewer").roles("USER"))
                .param("month", "2026-06"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Бронирований: 2")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/calendar/day/2026-06-15")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-booking-dialog")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-date=\"2026-06-15\"")));
    }

    @Test
    void calendarDayRendersBookingCreateButton() throws Exception {
        User actor = actor();
        BookingFilter filter = BookingFilter.EMPTY;
        UUID itemId = UUID.randomUUID();
        CalendarService.BookingLineSummary line = new CalendarService.BookingLineSummary(
                itemId,
                "Камеры",
                "Sony FX6",
                "Owner User",
                null,
                1);
        CalendarService.BookingSummary booking = new CalendarService.BookingSummary(
                UUID.randomUUID(),
                Instant.parse("2026-06-15T10:00:00Z"),
                Instant.parse("2026-06-15T12:00:00Z"),
                "Actor",
                "Интервью",
                List.of(line),
                true);
        CalendarService.DayCalendar day = new CalendarService.DayCalendar(
                LocalDate.parse("2026-06-15"),
                YearMonth.parse("2026-06"),
                List.of(booking),
                filter);
        when(currentUserService.requireCurrentUser()).thenReturn(actor);
        when(calendarService.dayCalendar(LocalDate.parse("2026-06-15"), actor, filter)).thenReturn(day);
        when(itemRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of());

        mockMvc.perform(get("/calendar/day/2026-06-15").with(user("viewer").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-booking-dialog")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-date=\"2026-06-15\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"booking-dialog\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/equipment/" + itemId)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Sony FX6")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Владелец: Owner User")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-reason-dialog")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/bookings/" + booking.id() + "/delete")));
    }

    @Test
    void calendarDayHidesDeleteButtonForOtherUsersBooking() throws Exception {
        User actor = actor();
        BookingFilter filter = BookingFilter.EMPTY;
        CalendarService.BookingSummary booking = new CalendarService.BookingSummary(
                UUID.randomUUID(),
                Instant.parse("2026-06-15T10:00:00Z"),
                Instant.parse("2026-06-15T12:00:00Z"),
                "Other User",
                null,
                List.of(),
                false);
        CalendarService.DayCalendar day = new CalendarService.DayCalendar(
                LocalDate.parse("2026-06-15"),
                YearMonth.parse("2026-06"),
                List.of(booking),
                filter);
        when(currentUserService.requireCurrentUser()).thenReturn(actor);
        when(calendarService.dayCalendar(LocalDate.parse("2026-06-15"), actor, filter)).thenReturn(day);
        when(itemRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of());

        mockMvc.perform(get("/calendar/day/2026-06-15").with(user("viewer").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/bookings/" + booking.id() + "/delete"))));
    }

    @Test
    void bookingDeleteFromCalendarRedirectsBackToDay() throws Exception {
        UUID bookingId = UUID.randomUUID();
        User actor = actor();
        when(currentUserService.requireCurrentUser()).thenReturn(actor);

        mockMvc.perform(post("/bookings/{id}/delete", bookingId)
                .with(user("viewer").roles("USER"))
                .with(csrf())
                .param("reason", "mistake")
                .param("returnUrl", "/calendar/day/2026-06-15"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar/day/2026-06-15"));

        verify(bookingService).deleteBooking(bookingId, "mistake", actor);
    }

    @Test
    void bookingDeleteRequiresCsrfToken() throws Exception {
        UUID bookingId = UUID.randomUUID();

        mockMvc.perform(post("/bookings/{id}/delete", bookingId)
                .with(user("viewer").roles("USER"))
                .param("reason", "cleanup"))
                .andExpect(status().isForbidden());

        verify(bookingService, never()).deleteBooking(any(), any(), any());
    }

    @Test
    void adminCategoryDeleteDelegatesToInventoryService() throws Exception {
        UUID categoryId = UUID.randomUUID();
        User actor = actor();
        when(currentUserService.requireCurrentUser()).thenReturn(actor);

        mockMvc.perform(post("/admin/categories/{id}/delete", categoryId)
                .with(user("admin").roles("ADMIN"))
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/equipment"));

        verify(inventoryService).deleteCategory(categoryId, actor);
    }

    @Test
    void adminCategoryDeleteRequiresAdminRole() throws Exception {
        UUID categoryId = UUID.randomUUID();

        mockMvc.perform(post("/admin/categories/{id}/delete", categoryId)
                .with(user("operator").roles("USER"))
                .with(csrf()))
                .andExpect(status().isForbidden());

        verify(inventoryService, never()).deleteCategory(any(), any());
    }

    @Test
    void adminCategoryDeleteRequiresCsrfToken() throws Exception {
        UUID categoryId = UUID.randomUUID();

        mockMvc.perform(post("/admin/categories/{id}/delete", categoryId)
                .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());

        verify(inventoryService, never()).deleteCategory(any(), any());
    }

    @Test
    void bookingModalPrefillsSelectedDateAndReturnUrl() throws Exception {
        mockMvc.perform(get("/bookings/modal")
                .with(user("viewer").roles("USER"))
                .param("date", "2026-06-20")
                .param("returnUrl", "/calendar?month=2026-06"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Новое бронирование")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"2026-06-20T09:00:00\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"2026-06-20T23:59:00\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"/calendar?month=2026-06\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hx-post=\"/bookings\"")));
    }

    @Test
    void bookingModalPrefillsTodayWithRoundedStartTime() throws Exception {
        mockMvc.perform(get("/bookings/modal")
                .with(user("viewer").roles("USER"))
                .param("date", "2026-06-15")
                .param("returnUrl", "/calendar/day/2026-06-15"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"2026-06-15T07:00:00\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"2026-06-15T23:59:00\"")));
    }

    @Test
    void bookingCreateRedirectsBackToCalendarWhenReturnUrlProvided() throws Exception {
        UUID quantityItemId = UUID.randomUUID();
        User actor = actor();
        when(currentUserService.requireCurrentUser()).thenReturn(actor);

        mockMvc.perform(post("/bookings")
                .with(user("viewer").roles("USER"))
                .with(csrf())
                .param("returnUrl", "/calendar?month=2026-06")
                .param("startsAt", "2026-06-15T09:00")
                .param("endsAt", "2026-06-15T10:00")
                .param("lines[0].equipmentItemId", quantityItemId.toString())
                .param("lines[0].quantity", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar?month=2026-06"));

        verify(bookingService).createBooking(any(BookingService.CreateBookingCommand.class), eq(actor));
    }

    @Test
    void bookingCreateFromModalUsesHtmxRedirectHeader() throws Exception {
        UUID quantityItemId = UUID.randomUUID();
        User actor = actor();
        when(currentUserService.requireCurrentUser()).thenReturn(actor);

        mockMvc.perform(post("/bookings")
                .with(user("viewer").roles("USER"))
                .with(csrf())
                .header("HX-Request", "true")
                .param("returnUrl", "/calendar/day/2026-06-15")
                .param("startsAt", "2026-06-15T09:00")
                .param("endsAt", "2026-06-15T10:00")
                .param("lines[0].equipmentItemId", quantityItemId.toString())
                .param("lines[0].quantity", "1"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Redirect", "/calendar/day/2026-06-15"));

        verify(bookingService).createBooking(any(BookingService.CreateBookingCommand.class), eq(actor));
    }

    @Test
    void bookingCreateRejectsUnsafeReturnUrl() throws Exception {
        UUID quantityItemId = UUID.randomUUID();
        User actor = actor();
        when(currentUserService.requireCurrentUser()).thenReturn(actor);

        mockMvc.perform(post("/bookings")
                .with(user("viewer").roles("USER"))
                .with(csrf())
                .param("returnUrl", "https://evil.example")
                .param("startsAt", "2026-06-15T09:00")
                .param("endsAt", "2026-06-15T10:00")
                .param("lines[0].equipmentItemId", quantityItemId.toString())
                .param("lines[0].quantity", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/bookings"));
    }

    @Test
    void bookingCreateValidationErrorsRerenderModalWhenReturnUrlProvided() throws Exception {
        mockMvc.perform(post("/bookings")
                .with(user("viewer").roles("USER"))
                .with(csrf())
                .param("returnUrl", "/calendar")
                .param("startsAt", "2026-06-15T09:00")
                .param("endsAt", "2026-06-15T10:00"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Новое бронирование")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"/calendar\"")));

        verify(bookingService, never()).createBooking(any(), any());
    }

    @Test
    void bookingsListRendersFilterPanelAndAppliesStatusFilter() throws Exception {
        User actor = actor();
        EquipmentItem item = item("Sony FX6", TrackingMode.UNIT, 0);
        item.assignOwner(actor);
        Booking booking = new Booking(actor, Instant.parse("2026-06-15T10:00:00Z"), Instant.parse("2026-06-15T12:00:00Z"), BookingStatus.BOOKED);
        ReflectionTestUtils.setField(booking, "id", UUID.randomUUID());
        booking.replaceLines(java.util.Set.of(new BookingLine(booking, item, null, 1)));
        when(currentUserService.requireCurrentUser()).thenReturn(actor);
        when(bookingService.findBookings(eq(actor), eq(BookingFilter.of(BookingStatus.BOOKED, null, null, null, null))))
                .thenReturn(List.of(booking));
        when(itemRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(item));

        mockMvc.perform(get("/bookings")
                .with(user("viewer").roles("USER"))
                .param("status", "BOOKED"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Все статусы")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Применить")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/equipment/" + item.getId())))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Sony FX6")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Владелец: Actor")));

        verify(bookingService).findBookings(eq(actor), eq(BookingFilter.of(BookingStatus.BOOKED, null, null, null, null)));
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
                    "0.2.0-test",
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
