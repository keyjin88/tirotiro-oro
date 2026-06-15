package oro.tirotiro.equipmentwarehouse.auth.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    @Query("select case when count(user) > 0 then true else false end "
            + "from User user join user.roles role where role.code = :roleCode")
    boolean existsByRoleCode(@Param("roleCode") RoleCode roleCode);

    @EntityGraph(attributePaths = {"roles", "permissionGrants", "permissionGrants.permission"})
    @Query("select user from User user order by user.email")
    java.util.List<User> findAllWithRolesAndPermissions();
}
