package oro.tirotiro.equipmentwarehouse.permission;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import oro.tirotiro.equipmentwarehouse.audit.AuditService;
import oro.tirotiro.equipmentwarehouse.auth.persistence.RoleCode;
import oro.tirotiro.equipmentwarehouse.auth.persistence.User;
import oro.tirotiro.equipmentwarehouse.auth.persistence.UserRepository;
import oro.tirotiro.equipmentwarehouse.permission.persistence.Permission;
import oro.tirotiro.equipmentwarehouse.permission.persistence.PermissionCode;
import oro.tirotiro.equipmentwarehouse.permission.persistence.PermissionRepository;
import oro.tirotiro.equipmentwarehouse.permission.persistence.UserPermission;
import oro.tirotiro.equipmentwarehouse.permission.persistence.UserPermissionId;
import oro.tirotiro.equipmentwarehouse.permission.persistence.UserPermissionRepository;

@Service
public class PermissionService {

    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;
    private final UserPermissionRepository userPermissionRepository;
    private final AuditService auditService;
    private final Clock clock;

    public PermissionService(
            UserRepository userRepository,
            PermissionRepository permissionRepository,
            UserPermissionRepository userPermissionRepository,
            AuditService auditService,
            Clock clock) {
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
        this.userPermissionRepository = userPermissionRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean hasPermission(UUID userId, PermissionCode permissionCode) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        if (isAdmin(user)) {
            return true;
        }
        return user.getPermissionGrants().stream()
                .anyMatch(grant -> grant.getPermission().getCode() == permissionCode);
    }

    public void requireEquipmentCreate(User user) {
        if (!isAdmin(user) && !hasPermission(user.getId(), PermissionCode.EQUIPMENT_CREATE)) {
            throw new AccessDeniedException("EQUIPMENT_CREATE permission is required");
        }
    }

    @Transactional
    public void grantPermission(UUID targetUserId, PermissionCode permissionCode, User actor) {
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + targetUserId));
        Permission permission = permissionRepository.findByCode(permissionCode)
                .orElseThrow(() -> new IllegalArgumentException("Permission not found: " + permissionCode));
        UserPermissionId id = new UserPermissionId(target.getId(), permission.getId());
        if (userPermissionRepository.existsById(id)) {
            return;
        }

        userPermissionRepository.save(new UserPermission(target, permission, actor, clock.instant()));
        auditService.record(actor, "PERMISSION_GRANTED", "USER", target.getId(), Map.of(
                "permission", permissionCode.name()));
    }

    @Transactional
    public void revokePermission(UUID targetUserId, PermissionCode permissionCode, User actor) {
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + targetUserId));
        Permission permission = permissionRepository.findByCode(permissionCode)
                .orElseThrow(() -> new IllegalArgumentException("Permission not found: " + permissionCode));
        UserPermissionId id = new UserPermissionId(target.getId(), permission.getId());
        if (!userPermissionRepository.existsById(id)) {
            return;
        }

        userPermissionRepository.deleteById(id);
        auditService.record(actor, "PERMISSION_REVOKED", "USER", target.getId(), Map.of(
                "permission", permissionCode.name()));
    }

    public boolean isAdmin(User user) {
        return userRepository.existsByIdAndRoleCode(user.getId(), RoleCode.ADMIN);
    }
}
