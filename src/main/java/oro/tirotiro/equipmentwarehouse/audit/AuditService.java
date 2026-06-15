package oro.tirotiro.equipmentwarehouse.audit;

import java.time.Clock;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import oro.tirotiro.equipmentwarehouse.audit.persistence.AuditLog;
import oro.tirotiro.equipmentwarehouse.audit.persistence.AuditLogRepository;
import oro.tirotiro.equipmentwarehouse.auth.persistence.User;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    public AuditService(AuditLogRepository auditLogRepository, Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AuditLog record(User actor, String action, String entityType, UUID entityId, Map<String, ?> details) {
        return auditLogRepository.save(new AuditLog(
                actor,
                action,
                entityType,
                entityId,
                toJson(details),
                clock.instant()));
    }

    private String toJson(Map<String, ?> details) {
        if (details == null || details.isEmpty()) {
            return "{}";
        }
        return details.entrySet().stream()
                .map(entry -> quote(entry.getKey()) + ":" + valueToJson(entry.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
    }

    private String valueToJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Collection<?> values) {
            return values.stream()
                    .map(this::valueToJson)
                    .collect(Collectors.joining(",", "[", "]"));
        }
        return quote(value.toString());
    }

    private String quote(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r") + "\"";
    }
}
