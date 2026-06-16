package oro.tirotiro.equipmentwarehouse.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import oro.tirotiro.equipmentwarehouse.audit.AuditService;
import oro.tirotiro.equipmentwarehouse.audit.persistence.AuditLogRepository;
import oro.tirotiro.equipmentwarehouse.auth.persistence.Role;
import oro.tirotiro.equipmentwarehouse.auth.persistence.RoleCode;
import oro.tirotiro.equipmentwarehouse.auth.persistence.RoleRepository;
import oro.tirotiro.equipmentwarehouse.auth.persistence.User;
import oro.tirotiro.equipmentwarehouse.auth.persistence.UserRepository;
import oro.tirotiro.equipmentwarehouse.booking.BookingService;
import oro.tirotiro.equipmentwarehouse.booking.persistence.BookingRepository;
import oro.tirotiro.equipmentwarehouse.config.AppProperties;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentItemRepository;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentUnitRepository;
import oro.tirotiro.equipmentwarehouse.permission.persistence.Permission;
import oro.tirotiro.equipmentwarehouse.permission.persistence.PermissionCode;
import oro.tirotiro.equipmentwarehouse.permission.persistence.UserPermission;
import oro.tirotiro.equipmentwarehouse.permission.persistence.UserPermissionRepository;

class AuthSupportTests {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatedUserExposesUserDetails() {
        User user = user("viewer@example.com");
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                user,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));

        assertThat(authenticatedUser.id()).isEqualTo(user.getId());
        assertThat(authenticatedUser.displayName()).isEqualTo("viewer@example.com");
        assertThat(authenticatedUser.getUsername()).isEqualTo("viewer@example.com");
        assertThat(authenticatedUser.getPassword()).isEqualTo("hash");
        assertThat(authenticatedUser.isEnabled()).isTrue();
        assertThat(authenticatedUser.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
    }

    @Test
    void databaseUserDetailsServiceMapsRolesAndPermissions() {
        User user = user("creator@example.com");
        user.getRoles().add(role(RoleCode.USER));
        user.getPermissionGrants().add(new UserPermission(
                user,
                permission(PermissionCode.EQUIPMENT_CREATE),
                null,
                java.time.Instant.parse("2026-06-15T09:00:00Z")));
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findByEmailIgnoreCase("creator@example.com")).thenReturn(Optional.of(user));

        AuthenticatedUser details = (AuthenticatedUser) new DatabaseUserDetailsService(userRepository)
                .loadUserByUsername("creator@example.com");

        assertThat(details.getAuthorities()).extracting("authority")
                .containsExactlyInAnyOrder("ROLE_USER", "EQUIPMENT_CREATE");
    }

    @Test
    void databaseUserDetailsServiceRejectsUnknownUser() {
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new DatabaseUserDetailsService(userRepository).loadUserByUsername("missing@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void currentUserServiceLoadsAuthenticatedPrincipal() {
        User user = user("viewer@example.com");
        AuthenticatedUser principal = new AuthenticatedUser(user, List.of());
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findByIdWithRolesAndPermissions(user.getId())).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));

        CurrentUserService service = new CurrentUserService(userRepository);

        assertThat(service.currentUserId()).contains(user.getId());
        assertThat(service.currentUser()).contains(user);
        assertThat(service.requireCurrentUser()).isEqualTo(user);
        verify(userRepository, never()).findById(user.getId());
    }

    @Test
    void currentUserServiceIgnoresAnonymousOrNonDomainPrincipal() {
        CurrentUserService service = new CurrentUserService(mock(UserRepository.class));

        assertThat(service.currentUserId()).isEmpty();

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("user", null, List.of()));
        assertThat(service.currentUserId()).isEmpty();
    }

    @Test
    void bootstrapAdminInitializerSkipsWhenAdminExists() {
        UserRepository userRepository = mock(UserRepository.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(userRepository.existsByRoleCode(RoleCode.ADMIN)).thenReturn(true);

        new BootstrapAdminInitializer(
                userRepository,
                roleRepository,
                passwordEncoder,
                appProperties(null, null, null))
                .run(mock(ApplicationArguments.class));

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(roleRepository, passwordEncoder);
    }

    @Test
    void bootstrapAdminInitializerCreatesEncodedAdminWhenNoneExists() {
        UserRepository userRepository = mock(UserRepository.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        Role adminRole = role(RoleCode.ADMIN);
        when(userRepository.existsByRoleCode(RoleCode.ADMIN)).thenReturn(false);
        when(roleRepository.findByCode(RoleCode.ADMIN)).thenReturn(Optional.of(adminRole));
        when(passwordEncoder.encode("secret")).thenReturn("bcrypt-hash");

        new BootstrapAdminInitializer(
                userRepository,
                roleRepository,
                passwordEncoder,
                appProperties(" admin@example.com ", "secret", " Root Admin "))
                .run(mock(ApplicationArguments.class));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue()).satisfies(user -> {
            assertThat(user.getEmail()).isEqualTo("admin@example.com");
            assertThat(user.getPasswordHash()).isEqualTo("bcrypt-hash");
            assertThat(user.getDisplayName()).isEqualTo("Root Admin");
            assertThat(user.getRoles()).containsExactly(adminRole);
        });
    }

    @Test
    void bootstrapAdminInitializerFailsClearlyWhenCredentialsAreMissing() {
        UserRepository userRepository = mock(UserRepository.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(userRepository.existsByRoleCode(RoleCode.ADMIN)).thenReturn(false);

        assertThatThrownBy(() -> new BootstrapAdminInitializer(
                userRepository,
                roleRepository,
                passwordEncoder,
                appProperties("", "", null))
                .run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_BOOTSTRAP_ADMIN_EMAIL")
                .hasMessageContaining("APP_BOOTSTRAP_ADMIN_PASSWORD");
        verifyNoInteractions(roleRepository, passwordEncoder);
    }

    @Test
    void registrationCreatesEnabledUserRoleOnlyAccountWithEncodedPassword() {
        UserRepository userRepository = mock(UserRepository.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        Role userRole = role(RoleCode.USER);
        when(userRepository.findByEmailIgnoreCase("viewer@example.com")).thenReturn(Optional.empty());
        when(roleRepository.findByCode(RoleCode.USER)).thenReturn(Optional.of(userRole));
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");

        new UserRegistrationService(userRepository, roleRepository, passwordEncoder)
                .register(new UserRegistrationService.RegisterUserCommand(
                        " Viewer@Example.com ",
                        " Viewer ",
                        "password123"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue()).satisfies(user -> {
            assertThat(user.getEmail()).isEqualTo("viewer@example.com");
            assertThat(user.getDisplayName()).isEqualTo("Viewer");
            assertThat(user.getPasswordHash()).isEqualTo("encoded-password");
            assertThat(user.isEnabled()).isTrue();
            assertThat(user.getRoles()).containsExactly(userRole);
            assertThat(user.getPermissionGrants()).isEmpty();
        });
    }

    @Test
    void registrationRejectsDuplicateEmailBeforeEncodingPassword() {
        UserRepository userRepository = mock(UserRepository.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(userRepository.findByEmailIgnoreCase("viewer@example.com")).thenReturn(Optional.of(user("viewer@example.com")));

        assertThatThrownBy(() -> new UserRegistrationService(userRepository, roleRepository, passwordEncoder)
                .register(new UserRegistrationService.RegisterUserCommand(
                        "viewer@example.com",
                        "Viewer",
                        "password123")))
                .isInstanceOf(UserRegistrationService.DuplicateEmailException.class)
                .hasMessageContaining("уже зарегистрирован");

        verifyNoInteractions(roleRepository, passwordEncoder);
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void userAdministrationGrantsAndRevokesAdminRoleWithAudit() {
        UserRepository userRepository = mock(UserRepository.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        AuditService auditService = mock(AuditService.class);
        User actor = user("admin@example.com");
        User target = user("target@example.com");
        Role adminRole = role(RoleCode.ADMIN);
        when(userRepository.findByIdWithRolesAndPermissions(target.getId())).thenReturn(Optional.of(target));
        when(roleRepository.findByCode(RoleCode.ADMIN)).thenReturn(Optional.of(adminRole));
        when(userRepository.countByRoleCode(RoleCode.ADMIN)).thenReturn(2L);
        UserAdministrationService service = userAdministrationService(userRepository, roleRepository, auditService);

        service.grantRole(target.getId(), RoleCode.ADMIN, actor);
        service.revokeRole(target.getId(), RoleCode.ADMIN, actor);

        assertThat(target.getRoles()).isEmpty();
        verify(auditService).record(eq(actor), eq("ROLE_GRANTED"), eq("USER"), eq(target.getId()), any());
        verify(auditService).record(eq(actor), eq("ROLE_REVOKED"), eq("USER"), eq(target.getId()), any());
    }

    @Test
    void userAdministrationPreservesUserRoleAndLastAdmin() {
        UserRepository userRepository = mock(UserRepository.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        AuditService auditService = mock(AuditService.class);
        User actor = user("admin@example.com");
        User target = user("target@example.com");
        target.getRoles().add(role(RoleCode.ADMIN));
        when(userRepository.findByIdWithRolesAndPermissions(target.getId())).thenReturn(Optional.of(target));
        when(userRepository.countByRoleCode(RoleCode.ADMIN)).thenReturn(1L);
        UserAdministrationService service = userAdministrationService(userRepository, roleRepository, auditService);

        assertThatThrownBy(() -> service.revokeRole(target.getId(), RoleCode.USER, actor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("USER нельзя отозвать");
        assertThatThrownBy(() -> service.revokeRole(target.getId(), RoleCode.ADMIN, actor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("последнего администратора");

        assertThat(target.getRoles()).extracting(Role::getCode).containsExactly(RoleCode.ADMIN);
        verifyNoInteractions(roleRepository, auditService);
    }

    @Test
    void userAdministrationDeletesUserWithReferenceCleanupAndAudit() {
        UserRepository userRepository = mock(UserRepository.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        AuditService auditService = mock(AuditService.class);
        BookingService bookingService = mock(BookingService.class);
        BookingRepository bookingRepository = mock(BookingRepository.class);
        UserPermissionRepository userPermissionRepository = mock(UserPermissionRepository.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        EquipmentItemRepository equipmentItemRepository = mock(EquipmentItemRepository.class);
        EquipmentUnitRepository equipmentUnitRepository = mock(EquipmentUnitRepository.class);
        User actor = user("admin@example.com");
        User target = user("target@example.com");
        target.getRoles().add(role(RoleCode.USER));
        when(userRepository.findByIdWithRolesAndPermissions(target.getId())).thenReturn(Optional.of(target));
        UserAdministrationService service = new UserAdministrationService(
                userRepository,
                roleRepository,
                auditService,
                bookingService,
                bookingRepository,
                userPermissionRepository,
                auditLogRepository,
                equipmentItemRepository,
                equipmentUnitRepository);

        service.deleteUser(target.getId(), actor);

        verify(bookingService).deleteAllBookingsForUser(target.getId(), actor);
        verify(userPermissionRepository).clearGrantedByReferences(target.getId());
        verify(userPermissionRepository).deleteByUserId(target.getId());
        verify(bookingRepository).clearCancelledByReferences(target.getId());
        verify(auditLogRepository).clearActorUserReferences(target.getId());
        verify(equipmentItemRepository).clearDeletedByReferences(target.getId());
        verify(equipmentUnitRepository).clearDeletedByReferences(target.getId());
        verify(auditService).record(eq(actor), eq("USER_DELETED"), eq("USER"), eq(target.getId()), any());
        verify(userRepository).delete(target);
        assertThat(target.getRoles()).isEmpty();
    }

    @Test
    void userAdministrationBlocksSelfDeleteAndLastAdmin() {
        UserRepository userRepository = mock(UserRepository.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        AuditService auditService = mock(AuditService.class);
        BookingService bookingService = mock(BookingService.class);
        BookingRepository bookingRepository = mock(BookingRepository.class);
        UserPermissionRepository userPermissionRepository = mock(UserPermissionRepository.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        EquipmentItemRepository equipmentItemRepository = mock(EquipmentItemRepository.class);
        EquipmentUnitRepository equipmentUnitRepository = mock(EquipmentUnitRepository.class);
        User actor = user("admin@example.com");
        User lastAdmin = user("last-admin@example.com");
        lastAdmin.getRoles().add(role(RoleCode.ADMIN));
        when(userRepository.findByIdWithRolesAndPermissions(actor.getId())).thenReturn(Optional.of(actor));
        when(userRepository.findByIdWithRolesAndPermissions(lastAdmin.getId())).thenReturn(Optional.of(lastAdmin));
        when(userRepository.countByRoleCode(RoleCode.ADMIN)).thenReturn(1L);
        UserAdministrationService service = new UserAdministrationService(
                userRepository,
                roleRepository,
                auditService,
                bookingService,
                bookingRepository,
                userPermissionRepository,
                auditLogRepository,
                equipmentItemRepository,
                equipmentUnitRepository);

        assertThatThrownBy(() -> service.deleteUser(actor.getId(), actor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("собственную учётную запись");
        assertThatThrownBy(() -> service.deleteUser(lastAdmin.getId(), actor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("последнего администратора");

        verify(userRepository, never()).delete(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(
                bookingService,
                userPermissionRepository,
                auditLogRepository,
                equipmentItemRepository,
                equipmentUnitRepository);
    }

    private UserAdministrationService userAdministrationService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            AuditService auditService) {
        return new UserAdministrationService(
                userRepository,
                roleRepository,
                auditService,
                mock(BookingService.class),
                mock(BookingRepository.class),
                mock(UserPermissionRepository.class),
                mock(AuditLogRepository.class),
                mock(EquipmentItemRepository.class),
                mock(EquipmentUnitRepository.class));
    }

    private User user(String email) {
        User user = new User(email, "hash", email);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    private Role role(RoleCode code) {
        Role role = new Role(code, code.name());
        ReflectionTestUtils.setField(role, "id", 1L);
        return role;
    }

    private Permission permission(PermissionCode code) {
        Permission permission = new Permission(code, code.name(), "Permission");
        ReflectionTestUtils.setField(permission, "id", 2L);
        return permission;
    }

    private AppProperties appProperties(String email, String password, String name) {
        return new AppProperties(
                java.time.ZoneId.of("UTC"),
                new AppProperties.Security(false),
                new AppProperties.BootstrapAdmin(email, password, name));
    }
}
