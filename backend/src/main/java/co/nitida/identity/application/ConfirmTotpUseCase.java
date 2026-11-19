package co.nitida.identity.application;

import co.nitida.identity.domain.AppUserRepository;
import co.nitida.identity.domain.Totp;
import co.nitida.identity.domain.TotpRecoveryCode;
import co.nitida.identity.domain.TotpRecoveryCodeRepository;
import co.nitida.shared.ApiException;
import co.nitida.shared.RateLimiter;
import co.nitida.shared.UuidV7;
import co.nitida.shared.audit.AuditService;
import co.nitida.shared.crypto.KeyCipher;
import co.nitida.shared.crypto.SecureTokens;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Step two: the first valid code proves the authenticator app holds the secret, and turns TOTP on. */
@Service
public class ConfirmTotpUseCase {
    public record Confirmation(boolean platformAdmin, List<String> recoveryCodes) {}

    private static final int RECOVERY_CODES = 8;

    private final AppUserRepository users;
    private final TotpRecoveryCodeRepository recoveryCodes;
    private final KeyCipher cipher;
    private final AuditService audit;
    private final Clock clock;
    // A 6-digit code is guessable in 1M tries: five per 15 minutes per user. shortcut: per-instance memory like the login limiter.
    private final RateLimiter attempts;

    ConfirmTotpUseCase(AppUserRepository users, TotpRecoveryCodeRepository recoveryCodes, KeyCipher cipher, AuditService audit, Clock clock) {
        this.users = users;
        this.recoveryCodes = recoveryCodes;
        this.cipher = cipher;
        this.audit = audit;
        this.clock = clock;
        this.attempts = new RateLimiter(5, Duration.ofMinutes(15).toMillis(), clock);
    }

    /** Recovery codes are returned once. {@code platformAdmin} lets the caller lift the session's enrollment restriction. */
    @Transactional
    public Confirmation execute(UUID userId, String code) {
        attempts.check(userId.toString());
        var user = users.findById(userId).orElseThrow(ApiException::forbidden);
        if (user.isTotpEnabled()) throw ApiException.conflict("TOTP_ALREADY_ENABLED", "La verificación en dos pasos ya está activa");
        if (user.getTotpSecret() == null) throw ApiException.conflict("TOTP_NOT_STARTED", "Primero solicite el código QR de configuración");
        byte[] secret = cipher.open(user.getTotpSecret(), user.getTotpNonce(), userId);
        var step = Totp.verify(secret, code, clock.instant().getEpochSecond(), 0);
        if (step.isEmpty()) throw ApiException.unprocessable("INVALID_TOTP", "El código no es válido: revise la hora del teléfono e intente con el siguiente");
        user.enableTotp(step.getAsLong());
        var codes = new ArrayList<String>(RECOVERY_CODES);
        for (int i = 0; i < RECOVERY_CODES; i++) {
            String plain = SecureTokens.newCode(10);
            codes.add(plain);
            recoveryCodes.save(new TotpRecoveryCode(UuidV7.next(), userId, SecureTokens.hash(plain)));
        }
        audit.record(null, userId, "TOTP_ENABLED", "app_user", userId, null);
        return new Confirmation(user.isPlatformAdmin(), List.copyOf(codes));
    }
}
