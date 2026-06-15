package oro.tirotiro.equipmentwarehouse.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import oro.tirotiro.equipmentwarehouse.auth.persistence.Role;
import oro.tirotiro.equipmentwarehouse.auth.persistence.RoleCode;
import oro.tirotiro.equipmentwarehouse.auth.persistence.User;
import oro.tirotiro.equipmentwarehouse.auth.persistence.UserRepository;
import oro.tirotiro.equipmentwarehouse.permission.persistence.Permission;
import oro.tirotiro.equipmentwarehouse.permission.persistence.PermissionCode;
import oro.tirotiro.equipmentwarehouse.permission.persistence.UserPermission;

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
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));

        CurrentUserService service = new CurrentUserService(userRepository);

        assertThat(service.currentUserId()).contains(user.getId());
        assertThat(service.currentUser()).contains(user);
        assertThat(service.requireCurrentUser()).isEqualTo(user);
    }

    @Test
    void currentUserServiceIgnoresAnonymousOrNonDomainPrincipal() {
        CurrentUserService service = new CurrentUserService(mock(UserRepository.class));

        assertThat(service.currentUserId()).isEmpty();

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("user", null, List.of()));
        assertThat(service.currentUserId()).isEmpty();
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
}
