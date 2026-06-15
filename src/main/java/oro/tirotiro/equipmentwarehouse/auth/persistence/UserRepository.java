package oro.tirotiro.equipmentwarehouse.auth.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = {"roles", "permissionGrants", "permissionGrants.permission"})
    @Query("select user from User user order by user.email")
    java.util.List<User> findAllWithRolesAndPermissions();
}
