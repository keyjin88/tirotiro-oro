package oro.tirotiro.equipmentwarehouse.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import oro.tirotiro.equipmentwarehouse.audit.AuditService;
import oro.tirotiro.equipmentwarehouse.auth.persistence.Role;
import oro.tirotiro.equipmentwarehouse.auth.persistence.RoleCode;
import oro.tirotiro.equipmentwarehouse.auth.persistence.User;
import oro.tirotiro.equipmentwarehouse.auth.persistence.UserRepository;
import oro.tirotiro.equipmentwarehouse.permission.persistence.Permission;
import oro.tirotiro.equipmentwarehouse.permission.persistence.PermissionCode;
import oro.tirotiro.equipmentwarehouse.permission.persistence.PermissionRepository;
import oro.tirotiro.equipmentwarehouse.permission.persistence.UserPermission;
import oro.tirotiro.equipmentwarehouse.permission.persistence.UserPermissionId;
import oro.tirotiro.equipmentwarehouse.permission.persistence.UserPermissionRepository;

class PermissionServiceTests {

    private static final Instant NOW = Instant.parse("2026-06-15T09:00:00Z");

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PermissionRepository permissionRepository = mock(PermissionRepository.class);
    private final UserPermissionRepository userPermissionRepository = mock(UserPermissionRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final PermissionService permissionService = new PermissionService(
            userRepository,
            permissionRepository,
            userPermissionRepository,
            auditService,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void adminsHaveAllPermissionsWithoutExplicitGrant() {
        User admin = user("admin@example.com");
        admin.getRoles().add(role(RoleCode.ADMIN));
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));

        assertThat(permissionService.hasPermission(admin.getId(), PermissionCode.EQUIPMENT_CREATE)).isTrue();

        verify(userRepository).findById(admin.getId());
        verify(userPermissionRepository, never()).existsById(any());
    }

    @Test
    void requireEquipmentCreateRejectsUserWithoutGrant() {
        User user = user("user@example.com");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> permissionService.requireEquipmentCreate(user))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("EQUIPMENT_CREATE");
    }

    @Test
    void grantsAndRevokesPermissionWithAudit() {
        User actor = user("admin@example.com");
        User target = user("target@example.com");
        Permission permission = permission();
        UserPermissionId id = new UserPermissionId(target.getId(), permission.getId());
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(permissionRepository.findByCode(PermissionCode.EQUIPMENT_CREATE)).thenReturn(Optional.of(permission));
        when(userPermissionRepository.existsById(id)).thenReturn(false, true);

        permissionService.grantPermission(target.getId(), PermissionCode.EQUIPMENT_CREATE, actor);
        permissionService.revokePermission(target.getId(), PermissionCode.EQUIPMENT_CREATE, actor);

        verify(userPermissionRepository).save(any(UserPermission.class));
        verify(userPermissionRepository).deleteById(id);
        verify(auditService).record(eq(actor), eq("PERMISSION_GRANTED"), eq("USER"), eq(target.getId()), any());
        verify(auditService).record(eq(actor), eq("PERMISSION_REVOKED"), eq("USER"), eq(target.getId()), any());
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

    private Permission permission() {
        Permission permission = new Permission(PermissionCode.EQUIPMENT_CREATE, "Create equipment", "Can create");
        ReflectionTestUtils.setField(permission, "id", 7L);
        return permission;
    }
}
