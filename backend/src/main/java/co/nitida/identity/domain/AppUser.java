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
@Table(schema = "identity", name = "app_user")
public class AppUser extends AssignedIdEntity {
    static final int LOCK_AFTER_FAILURES = 5;
    static final long MAX_LOCK_MINUTES = 60;

    @Column(nullable = false, unique = true)
    private String email;
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    @Column(name = "display_name", nullable = false)
    private String displayName;
    @Column(name = "platform_admin", nullable = false)
    private boolean platformAdmin;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "locked_until")
    private Instant lockedUntil;
    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;
    @Column(name = "totp_secret")
    private byte[] totpSecret;
    @Column(name = "totp_nonce")
    private byte[] totpNonce;
    @Column(name = "totp_enabled", nullable = false)
    private boolean totpEnabled;
    @Column(name = "totp_last_step", nullable = false)
    private long totpLastStep;

    protected AppUser() {}

    public AppUser(UUID id, String email, String passwordHash, String displayName, boolean platformAdmin, Instant now) {
        super(id);
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.platformAdmin = platformAdmin;
        this.createdAt = now;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /** Progressive lock: from the 5th consecutive failure, 1, 2, 4... minutes up to 60 (A07). */
    public void registerFailure(Instant now) {
        failedAttempts++;
        if (failedAttempts >= LOCK_AFTER_FAILURES) {
            long minutes = Math.min(MAX_LOCK_MINUTES, 1L << Math.min(20, failedAttempts - LOCK_AFTER_FAILURES));
            lockedUntil = now.plus(Duration.ofMinutes(minutes));
        }
    }

    public void registerSuccess() {
        failedAttempts = 0;
        lockedUntil = null;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /** Stores a freshly generated (sealed) secret; it only counts once {@link #enableTotp} proves the app has it. */
    public void startTotpEnrollment(byte[] sealedSecret, byte[] nonce) {
        if (totpEnabled) throw ApiException.conflict("TOTP_ALREADY_ENABLED", "La verificación en dos pasos ya está activa");
        totpSecret = sealedSecret;
        totpNonce = nonce;
        totpLastStep = 0;
    }

    public void enableTotp(long confirmedStep) {
        if (totpEnabled) throw ApiException.conflict("TOTP_ALREADY_ENABLED", "La verificación en dos pasos ya está activa");
        if (totpSecret == null) throw ApiException.conflict("TOTP_NOT_STARTED", "Primero solicite el código QR de configuración");
        totpEnabled = true;
        totpLastStep = confirmedStep;
    }

    public void acceptTotpStep(long step) { totpLastStep = step; }

    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public boolean isPlatformAdmin() { return platformAdmin; }
    public boolean isTotpEnabled() { return totpEnabled; }
    public byte[] getTotpSecret() { return totpSecret; }
    public byte[] getTotpNonce() { return totpNonce; }
    public long getTotpLastStep() { return totpLastStep; }
    public int getFailedAttempts() { return failedAttempts; }
    public Instant getLockedUntil() { return lockedUntil; }
}
