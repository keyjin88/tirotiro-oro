package oro.tirotiro.equipmentwarehouse.permission.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    Optional<Permission> findByCode(PermissionCode code);
}
