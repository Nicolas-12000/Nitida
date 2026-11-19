package co.nitida.identity.domain;

import co.nitida.shared.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One-time backup for a lost authenticator. Only the hash is stored. */
@Entity
@Table(schema = "identity", name = "totp_recovery_code")
public class TotpRecoveryCode extends AssignedIdEntity {
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "code_hash", nullable = false)
    private String codeHash;
    @Column(name = "used_at")
    private Instant usedAt;

    protected TotpRecoveryCode() {}

    public TotpRecoveryCode(UUID id, UUID userId, String codeHash) {
        super(id);
        this.userId = userId;
        this.codeHash = codeHash;
    }

    public boolean consume(Instant now) {
        if (usedAt != null) return false;
        usedAt = now;
        return true;
    }
}
