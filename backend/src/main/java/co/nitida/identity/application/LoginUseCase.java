package co.nitida.identity.application;

import co.nitida.identity.IdentityApi.UserSummary;
import co.nitida.identity.domain.AppUserRepository;
import co.nitida.shared.ApiException;
import co.nitida.shared.RateLimiter;
import java.time.Clock;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginUseCase {
    public record Command(String email, String password, String ip) {}

    private static final String GENERIC = "Correo o contraseña incorrectos";

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final RateLimiter ipLimiter;
    private final RateLimiter emailLimiter;
    /** Hash of a random password: lets us spend the same time when the account does not exist. */
    private final String dummyHash;

    LoginUseCase(AppUserRepository users, PasswordEncoder encoder, Clock clock,
                 @org.springframework.beans.factory.annotation.Value("${nitida.login.ip-limit:10}") int ipLimit) {
        this.users = users;
        this.encoder = encoder;
        this.clock = clock;
        this.ipLimiter = new RateLimiter(ipLimit, Duration.ofMinutes(15).toMillis(), clock);
        this.emailLimiter = new RateLimiter(10, Duration.ofMinutes(15).toMillis(), clock);
        this.dummyHash = encoder.encode(co.nitida.shared.crypto.SecureTokens.newToken());
    }

    /** Failures must persist the counter, so a business error must not roll the transaction back. */
    @Transactional(noRollbackFor = ApiException.class)
    public UserSummary execute(Command c) {
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
        user.registerSuccess();
        return IdentityApiImpl.summary(user);
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", GENERIC);
    }
}
