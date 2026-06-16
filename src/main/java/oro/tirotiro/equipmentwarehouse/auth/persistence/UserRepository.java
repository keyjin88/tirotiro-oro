package oro.tirotiro.equipmentwarehouse.auth.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = {"roles", "permissionGrants", "permissionGrants.permission"})
    @Query("select user from User user where user.id = :id")
    Optional<User> findByIdWithRolesAndPermissions(@Param("id") UUID id);

    @Query("select case when count(user) > 0 then true else false end "
            + "from User user join user.roles role where role.code = :roleCode")
    boolean existsByRoleCode(@Param("roleCode") RoleCode roleCode);

    @Query("select count(user) from User user join user.roles role where role.code = :roleCode")
    long countByRoleCode(@Param("roleCode") RoleCode roleCode);

    @Query("select case when count(user) > 0 then true else false end "
            + "from User user join user.roles role where user.id = :userId and role.code = :roleCode")
    boolean existsByIdAndRoleCode(@Param("userId") UUID userId, @Param("roleCode") RoleCode roleCode);

    @EntityGraph(attributePaths = {"roles", "permissionGrants", "permissionGrants.permission"})
    @Query("select user from User user order by user.email")
    java.util.List<User> findAllWithRolesAndPermissions();

    java.util.List<User> findAllByOrderByDisplayNameAsc();

    java.util.List<User> findAllByEnabledTrueOrderByDisplayNameAsc();
}
