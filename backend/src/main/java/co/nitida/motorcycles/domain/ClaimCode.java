package co.nitida.motorcycles.domain;

import co.nitida.shared.ApiException;
import co.nitida.shared.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Single-use code (RN-80 claim, RN-82 transfer). Only the SHA-256 is stored; the plain code is shown once. */
@Entity
@Table(schema = "motorcycles", name = "claim_code")
public class ClaimCode extends AssignedIdEntity {
    public static final String DELIVERY = "DELIVERY";
    public static final String TRANSFER = "TRANSFER";

    @Column(name = "motorcycle_id", nullable = false)
    private UUID motorcycleId;
    @Column(nullable = false)
    private String kind;
    @Column(name = "code_hash", nullable = false, unique = true)
    private String codeHash;
    @Column(name = "created_by")
    private UUID createdBy;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "used_at")
    private Instant usedAt;

    protected ClaimCode() {}

    /** Codes are typed by hand: case and surrounding spaces do not matter. */
    public static String normalize(String code) {
        return code == null ? "" : code.strip().toUpperCase(java.util.Locale.ROOT);
    }

    public ClaimCode(UUID id, UUID motorcycleId, String kind, String codeHash, UUID createdBy, Instant expiresAt) {
        super(id);
        this.motorcycleId = motorcycleId;
        this.kind = kind;
        this.codeHash = codeHash;
        this.createdBy = createdBy;
        this.expiresAt = expiresAt;
    }

    /** Unknown, used and expired codes all look the same: not found (no oracle). */
    public void use(Instant now) {
        if (usedAt != null || !now.isBefore(expiresAt)) throw ApiException.notFound();
        usedAt = now;
    }

    public UUID getMotorcycleId() { return motorcycleId; }
    public String getKind() { return kind; }
    public UUID getCreatedBy() { return createdBy; }
}
