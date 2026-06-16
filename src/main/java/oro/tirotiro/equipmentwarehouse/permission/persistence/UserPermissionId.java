package oro.tirotiro.equipmentwarehouse.permission.persistence;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class UserPermissionId implements Serializable {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "permission_id")
    private Long permissionId;

    protected UserPermissionId() {
    }

    public UserPermissionId(UUID userId, Long permissionId) {
        this.userId = userId;
        this.permissionId = permissionId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Long getPermissionId() {
        return permissionId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UserPermissionId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && Objects.equals(permissionId, that.permissionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, permissionId);
    }
}
