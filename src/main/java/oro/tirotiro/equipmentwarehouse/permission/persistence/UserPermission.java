package oro.tirotiro.equipmentwarehouse.permission.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import oro.tirotiro.equipmentwarehouse.auth.persistence.User;

@Entity
@Table(name = "user_permissions")
public class UserPermission {

    @EmbeddedId
    private UserPermissionId id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @MapsId("permissionId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "permission_id", nullable = false)
    private Permission permission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by")
    private User grantedBy;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    protected UserPermission() {
    }

    public UserPermission(User user, Permission permission, User grantedBy, Instant grantedAt) {
        this.user = user;
        this.permission = permission;
        this.grantedBy = grantedBy;
        this.grantedAt = grantedAt;
        this.id = new UserPermissionId(user.getId(), permission.getId());
    }

    public UserPermissionId getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Permission getPermission() {
        return permission;
    }

    public User getGrantedBy() {
        return grantedBy;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }
}
