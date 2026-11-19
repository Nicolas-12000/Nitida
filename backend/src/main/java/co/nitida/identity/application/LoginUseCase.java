package co.nitida.identity.application;

import co.nitida.identity.IdentityApi.UserSummary;
import co.nitida.identity.domain.AppUserRepository;
import co.nitida.identity.domain.Totp;
import co.nitida.shared.ApiException;
import co.nitida.shared.RateLimiter;
import co.nitida.shared.crypto.KeyCipher;
import java.time.Clock;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginUseCase {
    public record Command(String email, String password, String totp, String ip) {}

    /** {@code adminAllowed}: the session may carry platform-admin privileges (TOTP enrolled, or the policy is off). */
    public record Result(UserSummary user, boolean adminAllowed) {}

    private static final String GENERIC = "Correo o contraseña incorrectos";

    private final AppUserRepository users;
    private final KeyCipher cipher;
    private final boolean totpRequired;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final RateLimiter ipLimiter;
    private final RateLimiter emailLimiter;
    /** Hash of a random password: lets us spend the same time when the account does not exist. */
    private final String dummyHash;

    LoginUseCase(AppUserRepository users, PasswordEncoder encoder, Clock clock, KeyCipher cipher,
                 @org.springframework.beans.factory.annotation.Value("${nitida.admin.totp-required:true}") boolean totpRequired,
                 @org.springframework.beans.factory.annotation.Value("${nitida.login.ip-limit:10}") int ipLimit) {
        this.users = users;
        this.cipher = cipher;
        this.totpRequired = totpRequired;
        this.encoder = encoder;
        this.clock = clock;
        this.ipLimiter = new RateLimiter(ipLimit, Duration.ofMinutes(15).toMillis(), clock);
        this.emailLimiter = new RateLimiter(10, Duration.ofMinutes(15).toMillis(), clock);
        this.dummyHash = encoder.encode(co.nitida.shared.crypto.SecureTokens.newToken());
    }

    /** Failures must persist the counter, so a business error must not roll the transaction back. */
    @Transactional(noRollbackFor = ApiException.class)
    public Result execute(Command c) {
        String email = c.email() == null ? "" : c.email().strip().toLowerCase();
        ipLimiter.check(c.ip());
        emailLimiter.check(email);
        var now = clock.instant();
        var user = users.findByEmail(email).orElse(null);
        String password = c.password() == null ? "" : c.password();
        if (user == null) {
            encoder.matches(password, dummyHash);
            throw invalid();
        }
        if (user.isLocked(now)) {
            throw invalid();
        }
        if (!encoder.matches(password, user.getPasswordHash())) {
            user.registerFailure(now);
            throw invalid();
        }
        if (user.isTotpEnabled()) {
            // Only reached with the right password, so asking for the code reveals nothing to someone without it.
            if (c.totp() == null || c.totp().isBlank()) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "TOTP_REQUIRED", "Ingrese el código de su aplicación de autenticación");
            }
            byte[] secret = cipher.open(user.getTotpSecret(), user.getTotpNonce(), user.getId());
            var step = Totp.verify(secret, c.totp(), now.getEpochSecond(), user.getTotpLastStep());
            if (step.isEmpty()) {
                user.registerFailure(now); // a wrong code counts toward the progressive lock like a wrong password
                throw invalid();
            }
            user.acceptTotpStep(step.getAsLong());
        }
        user.registerSuccess();
        var summary = IdentityApiImpl.summary(user);
        return new Result(summary, summary.platformAdmin() && (summary.totpEnabled() || !totpRequired));
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", GENERIC);
    }
}
