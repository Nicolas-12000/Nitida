package co.nitida.identity.domain;

import co.nitida.shared.ApiException;
import co.nitida.shared.Role;
import co.nitida.shared.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "identity", name = "invitation")
public class Invitation extends AssignedIdEntity {
    public static final Duration VALIDITY = Duration.ofHours(72);

    @Column(name = "workshop_id", nullable = false)
    private UUID workshopId;
    @Column(nullable = false)
    private String email;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;
    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "used_at")
    private Instant usedAt;

    protected Invitation() {}

    public Invitation(UUID id, UUID workshopId, String email, Role role, String tokenHash, UUID createdBy, Instant now) {
        super(id);
        this.workshopId = workshopId;
        this.email = email;
        this.role = role;
        this.tokenHash = tokenHash;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.expiresAt = now.plus(VALIDITY);
    }

    /** Single use, 72 h, only for the invited email. Any failure looks like "not found" (no oracle). */
    public void accept(String acceptingEmail, Instant now) {
        if (usedAt != null || !now.isBefore(expiresAt) || !email.equalsIgnoreCase(acceptingEmail)) {
            throw ApiException.notFound();
        }
        usedAt = now;
    }

    public UUID getWorkshopId() { return workshopId; }
    public String getEmail() { return email; }
    public Role getRole() { return role; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
}
