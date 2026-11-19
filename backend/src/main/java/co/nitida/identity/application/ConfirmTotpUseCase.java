package co.nitida.identity.application;

import co.nitida.identity.domain.AppUserRepository;
import co.nitida.identity.domain.Totp;
import co.nitida.shared.ApiException;
import co.nitida.shared.RateLimiter;
import co.nitida.shared.audit.AuditService;
import co.nitida.shared.crypto.KeyCipher;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Step two: the first valid code proves the authenticator app holds the secret, and turns TOTP on. */
@Service
public class ConfirmTotpUseCase {
    private final AppUserRepository users;
    private final KeyCipher cipher;
    private final AuditService audit;
    private final Clock clock;
    // A 6-digit code is guessable in 1M tries: five per 15 minutes per user. shortcut: per-instance memory like the login limiter.
    private final RateLimiter attempts;

    ConfirmTotpUseCase(AppUserRepository users, KeyCipher cipher, AuditService audit, Clock clock) {
        this.users = users;
        this.cipher = cipher;
        this.audit = audit;
        this.clock = clock;
        this.attempts = new RateLimiter(5, Duration.ofMinutes(15).toMillis(), clock);
    }

    /** Returns whether the user is a platform admin, so the caller can lift the session's enrollment restriction. */
    @Transactional
    public boolean execute(UUID userId, String code) {
        attempts.check(userId.toString());
        var user = users.findById(userId).orElseThrow(ApiException::forbidden);
        if (user.isTotpEnabled()) throw ApiException.conflict("TOTP_ALREADY_ENABLED", "La verificación en dos pasos ya está activa");
        if (user.getTotpSecret() == null) throw ApiException.conflict("TOTP_NOT_STARTED", "Primero solicite el código QR de configuración");
        byte[] secret = cipher.open(user.getTotpSecret(), user.getTotpNonce(), userId);
        var step = Totp.verify(secret, code, clock.instant().getEpochSecond(), 0);
        if (step.isEmpty()) throw ApiException.unprocessable("INVALID_TOTP", "El código no es válido: revise la hora del teléfono e intente con el siguiente");
        user.enableTotp(step.getAsLong());
        audit.record(null, userId, "TOTP_ENABLED", "app_user", userId, null);
        return user.isPlatformAdmin();
    }
}
