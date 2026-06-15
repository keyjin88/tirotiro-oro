package oro.tirotiro.equipmentwarehouse.permission.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPermissionRepository extends JpaRepository<UserPermission, UserPermissionId> {

    List<UserPermission> findByUser_Id(UUID userId);
}
