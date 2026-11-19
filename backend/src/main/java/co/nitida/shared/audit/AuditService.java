package co.nitida.shared.audit;

import co.nitida.shared.UuidV7;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Insert-only audit trail (audit.audit_event). Details must never carry personal data. */
@Component
public class AuditService {
    private final JdbcClient jdbc;

    AuditService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void record(UUID workshopId, UUID userId, String action, String resource, UUID resourceId, String detailsJson) {
        jdbc.sql("""
                INSERT INTO audit.audit_event(id, workshop_id, user_id, action, resource, resource_id, created_at, details)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(UuidV7.next(), workshopId, userId, action, resource, resourceId,
                        java.sql.Timestamp.from(Instant.now()), detailsJson == null ? "{}" : detailsJson)
                .update();
    }
}
