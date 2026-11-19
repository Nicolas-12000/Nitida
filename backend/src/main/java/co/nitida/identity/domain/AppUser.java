package co.nitida.identity.domain;

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

    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public boolean isPlatformAdmin() { return platformAdmin; }
    public int getFailedAttempts() { return failedAttempts; }
    public Instant getLockedUntil() { return lockedUntil; }
}
