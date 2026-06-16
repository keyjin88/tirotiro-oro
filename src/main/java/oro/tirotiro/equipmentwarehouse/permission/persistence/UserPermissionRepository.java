package oro.tirotiro.equipmentwarehouse.permission.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserPermissionRepository extends JpaRepository<UserPermission, UserPermissionId> {

    List<UserPermission> findByUser_Id(UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from UserPermission userPermission where userPermission.user.id = :userId")
    void deleteByUserId(@Param("userId") UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update UserPermission userPermission set userPermission.grantedBy = null where userPermission.grantedBy.id = :userId")
    void clearGrantedByReferences(@Param("userId") UUID userId);
}
