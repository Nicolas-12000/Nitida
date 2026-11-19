package co.nitida.identity.domain;

import co.nitida.shared.ApiException;
import co.nitida.shared.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "identity", name = "password_reset")
public class PasswordReset extends AssignedIdEntity {
    public static final Duration VALIDITY = Duration.ofHours(1);

    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "used_at")
    private Instant usedAt;

    protected PasswordReset() {}

    public PasswordReset(UUID id, UUID userId, String tokenHash, Instant now) {
        super(id);
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.createdAt = now;
        this.expiresAt = now.plus(VALIDITY);
    }

    /** Single use, one hour. A used, expired or unknown token all look like "not found". */
    public void consume(Instant now) {
        if (usedAt != null || !now.isBefore(expiresAt)) throw ApiException.notFound();
        usedAt = now;
    }

    public UUID getUserId() { return userId; }
}
