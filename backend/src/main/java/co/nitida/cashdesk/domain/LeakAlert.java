package co.nitida.cashdesk.domain;

import co.nitida.shared.ApiException;
import co.nitida.shared.Texts;
import co.nitida.shared.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "cashdesk", name = "leak_alert")
public class LeakAlert extends AssignedIdEntity {
    @Column(name = "workshop_id", nullable = false)
    private UUID workshopId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertType type;
    @Column(name = "reference_id")
    private UUID referenceId;
    @Column(name = "dedupe_key", nullable = false)
    private String dedupeKey;
    @Column(nullable = false)
    private String details;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "resolved_at")
    private Instant resolvedAt;
    @Column(name = "resolved_by")
    private UUID resolvedBy;
    private String comment;

    protected LeakAlert() {}

    public LeakAlert(UUID id, UUID workshopId, AlertType type, UUID referenceId, String dedupeKey, String details, Instant now) {
        super(id);
        this.workshopId = workshopId;
        this.type = type;
        this.referenceId = referenceId;
        this.dedupeKey = dedupeKey;
        this.details = details;
        this.createdAt = now;
    }

    /** RN-53: alerts are never deleted; they are attended once, with a comment. */
    public void resolve(UUID by, String comment, Instant now) {
        String c = Texts.requireLength(comment, 5, 500, "COMMENT_REQUIRED", "Escriba un comentario (5 a 500 caracteres)");
        if (resolvedAt != null) throw ApiException.conflict("ALERT_ALREADY_RESOLVED", "La alerta ya fue atendida");
        resolvedAt = now;
        resolvedBy = by;
        this.comment = c;
    }

    public boolean isResolved() { return resolvedAt != null; }

    public UUID getWorkshopId() { return workshopId; }
    public AlertType getType() { return type; }
    public UUID getReferenceId() { return referenceId; }
    public String getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public String getComment() { return comment; }
}
