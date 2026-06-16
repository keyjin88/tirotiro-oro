package oro.tirotiro.equipmentwarehouse.audit.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, UUID entityId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AuditLog auditLog set auditLog.actorUser = null where auditLog.actorUser.id = :userId")
    void clearActorUserReferences(@Param("userId") UUID userId);
}
