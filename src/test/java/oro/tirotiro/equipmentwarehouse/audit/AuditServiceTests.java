package oro.tirotiro.equipmentwarehouse.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import oro.tirotiro.equipmentwarehouse.audit.persistence.AuditLog;
import oro.tirotiro.equipmentwarehouse.audit.persistence.AuditLogRepository;
import oro.tirotiro.equipmentwarehouse.auth.persistence.User;

class AuditServiceTests {

    @Test
    void recordsAuditDetailsAsJson() {
        Instant now = Instant.parse("2026-06-15T09:00:00Z");
        AuditLogRepository repository = mock(AuditLogRepository.class);
        AuditService auditService = new AuditService(repository, Clock.fixed(now, ZoneOffset.UTC));
        User actor = actor();
        UUID entityId = UUID.randomUUID();
        when(repository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuditLog log = auditService.record(actor, "UPDATED", "EQUIPMENT", entityId, Map.of(
                "name", "Camera \"A\"",
                "count", 2,
                "active", true,
                "notes", List.of("ready", "line\nbreak")));

        assertThat(log.getActorUser()).isEqualTo(actor);
        assertThat(log.getAction()).isEqualTo("UPDATED");
        assertThat(log.getEntityType()).isEqualTo("EQUIPMENT");
        assertThat(log.getEntityId()).isEqualTo(entityId);
        assertThat(log.getCreatedAt()).isEqualTo(now);
        assertThat(log.getDetails())
                .contains("\"count\":2")
                .contains("\"active\":true")
                .contains("\\\"A\\\"")
                .contains("line\\nbreak");
    }

    @Test
    void recordsEmptyDetailsObjectWhenDetailsAreMissing() {
        AuditLogRepository repository = mock(AuditLogRepository.class);
        AuditService auditService = new AuditService(repository, Clock.systemUTC());
        when(repository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(auditService.record(actor(), "VIEWED", "USER", UUID.randomUUID(), null).getDetails())
                .isEqualTo("{}");
    }

    private User actor() {
        User user = new User("actor@example.com", "hash", "Actor");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }
}
