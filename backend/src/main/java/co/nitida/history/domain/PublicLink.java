package co.nitida.history.domain;

import co.nitida.shared.ApiException;
import co.nitida.shared.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "history", name = "public_link")
public class PublicLink extends AssignedIdEntity {
    public static final int DEFAULT_DAYS = 30;

    @Column(name = "motorcycle_id", nullable = false)
    private UUID motorcycleId;
    @Column(name = "workshop_id")
    private UUID workshopId;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected PublicLink() {}

    /** RN-72: 1..365 days, default 30. Only the hash of the token is kept (RN-71). */
    public PublicLink(UUID id, UUID motorcycleId, UUID workshopId, UUID createdBy, String tokenHash, Integer days, Instant now) {
        super(id);
        int d = days == null ? DEFAULT_DAYS : days;
        if (d < 1 || d > 365) throw ApiException.unprocessable("INVALID_EXPIRATION", "El vencimiento debe estar entre 1 y 365 días");
        this.motorcycleId = motorcycleId;
        this.workshopId = workshopId;
        this.createdBy = createdBy;
        this.tokenHash = tokenHash;
        this.createdAt = now;
        this.expiresAt = now.plus(Duration.ofDays(d));
    }

    /** Valid while now < expiresAt and not revoked. */
    public boolean isValidAt(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    public void revoke(Instant now) {
        if (revokedAt == null) revokedAt = now;
    }

    public UUID getMotorcycleId() { return motorcycleId; }
    public UUID getWorkshopId() { return workshopId; }
    public Instant getExpiresAt() { return expiresAt; }
}
