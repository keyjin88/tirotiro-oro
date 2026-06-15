package oro.tirotiro.equipmentwarehouse.auth;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import oro.tirotiro.equipmentwarehouse.audit.AuditService;
import oro.tirotiro.equipmentwarehouse.auth.persistence.Role;
import oro.tirotiro.equipmentwarehouse.auth.persistence.RoleCode;
import oro.tirotiro.equipmentwarehouse.auth.persistence.RoleRepository;
import oro.tirotiro.equipmentwarehouse.auth.persistence.User;
import oro.tirotiro.equipmentwarehouse.auth.persistence.UserRepository;

@Service
public class UserAdministrationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuditService auditService;

    public UserAdministrationService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            AuditService auditService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.auditService = auditService;
    }

    @Transactional
    public void grantRole(UUID targetUserId, RoleCode roleCode, User actor) {
        User target = loadTarget(targetUserId);
        if (hasRole(target, roleCode)) {
            return;
        }
        Role role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new IllegalArgumentException("Роль не найдена: " + roleCode));
        target.getRoles().add(role);
        auditService.record(actor, "ROLE_GRANTED", "USER", target.getId(), Map.of("role", roleCode.name()));
    }

    @Transactional
    public void revokeRole(UUID targetUserId, RoleCode roleCode, User actor) {
        if (roleCode == RoleCode.USER) {
            throw new IllegalArgumentException("Базовую роль USER нельзя отозвать.");
        }

        User target = loadTarget(targetUserId);
        if (!hasRole(target, roleCode)) {
            return;
        }
        if (roleCode == RoleCode.ADMIN && userRepository.countByRoleCode(RoleCode.ADMIN) <= 1) {
            throw new IllegalArgumentException("Нельзя отозвать роль ADMIN у последнего администратора.");
        }

        target.getRoles().removeIf(role -> role.getCode() == roleCode);
        auditService.record(actor, "ROLE_REVOKED", "USER", target.getId(), Map.of("role", roleCode.name()));
    }

    private User loadTarget(UUID targetUserId) {
        return userRepository.findByIdWithRolesAndPermissions(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + targetUserId));
    }

    private boolean hasRole(User user, RoleCode roleCode) {
        return user.getRoles().stream().anyMatch(role -> role.getCode() == roleCode);
    }
}
