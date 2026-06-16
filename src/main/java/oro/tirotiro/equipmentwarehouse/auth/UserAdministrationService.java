package oro.tirotiro.equipmentwarehouse.auth;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import oro.tirotiro.equipmentwarehouse.audit.AuditService;
import oro.tirotiro.equipmentwarehouse.audit.persistence.AuditLogRepository;
import oro.tirotiro.equipmentwarehouse.auth.persistence.Role;
import oro.tirotiro.equipmentwarehouse.auth.persistence.RoleCode;
import oro.tirotiro.equipmentwarehouse.auth.persistence.RoleRepository;
import oro.tirotiro.equipmentwarehouse.auth.persistence.User;
import oro.tirotiro.equipmentwarehouse.auth.persistence.UserRepository;
import oro.tirotiro.equipmentwarehouse.booking.BookingService;
import oro.tirotiro.equipmentwarehouse.booking.persistence.BookingRepository;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentItemRepository;
import oro.tirotiro.equipmentwarehouse.inventory.persistence.EquipmentUnitRepository;
import oro.tirotiro.equipmentwarehouse.permission.persistence.UserPermissionRepository;

@Service
public class UserAdministrationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuditService auditService;
    private final BookingService bookingService;
    private final BookingRepository bookingRepository;
    private final UserPermissionRepository userPermissionRepository;
    private final AuditLogRepository auditLogRepository;
    private final EquipmentItemRepository equipmentItemRepository;
    private final EquipmentUnitRepository equipmentUnitRepository;

    public UserAdministrationService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            AuditService auditService,
            BookingService bookingService,
            BookingRepository bookingRepository,
            UserPermissionRepository userPermissionRepository,
            AuditLogRepository auditLogRepository,
            EquipmentItemRepository equipmentItemRepository,
            EquipmentUnitRepository equipmentUnitRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.auditService = auditService;
        this.bookingService = bookingService;
        this.bookingRepository = bookingRepository;
        this.userPermissionRepository = userPermissionRepository;
        this.auditLogRepository = auditLogRepository;
        this.equipmentItemRepository = equipmentItemRepository;
        this.equipmentUnitRepository = equipmentUnitRepository;
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

    @Transactional
    public void deleteUser(UUID targetUserId, User actor) {
        if (targetUserId.equals(actor.getId())) {
            throw new IllegalArgumentException("Нельзя удалить собственную учётную запись.");
        }

        User target = loadTarget(targetUserId);
        if (hasRole(target, RoleCode.ADMIN) && userRepository.countByRoleCode(RoleCode.ADMIN) <= 1) {
            throw new IllegalArgumentException("Нельзя удалить последнего администратора.");
        }
        bookingService.deleteAllBookingsForUser(targetUserId, actor);

        userPermissionRepository.clearGrantedByReferences(targetUserId);
        userPermissionRepository.deleteByUserId(targetUserId);
        bookingRepository.clearCancelledByReferences(targetUserId);
        auditLogRepository.clearActorUserReferences(targetUserId);
        equipmentItemRepository.clearDeletedByReferences(targetUserId);
        equipmentUnitRepository.clearDeletedByReferences(targetUserId);

        target.getRoles().clear();
        auditService.record(actor, "USER_DELETED", "USER", target.getId(), Map.of(
                "email", target.getEmail(),
                "displayName", target.getDisplayName()));
        userRepository.delete(target);
    }

    private User loadTarget(UUID targetUserId) {
        return userRepository.findByIdWithRolesAndPermissions(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + targetUserId));
    }

    private boolean hasRole(User user, RoleCode roleCode) {
        return user.getRoles().stream().anyMatch(role -> role.getCode() == roleCode);
    }
}
