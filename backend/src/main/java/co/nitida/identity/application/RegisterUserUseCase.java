package co.nitida.identity.application;

import co.nitida.identity.domain.AppUser;
import co.nitida.identity.domain.AppUserRepository;
import co.nitida.identity.domain.PasswordPolicy;
import co.nitida.shared.Emails;
import co.nitida.shared.ApiException;
import co.nitida.shared.RateLimiter;
import co.nitida.shared.Texts;
import co.nitida.shared.UuidV7;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterUserUseCase {
    public record Command(String email, String password, String displayName, String ip) {}

    public record Result(UUID userId) {}

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final Clock clock;
    // shortcut: per-instance memory like the login limiter, move to a shared store with more than one node
    private final RateLimiter ipLimiter;

    RegisterUserUseCase(AppUserRepository users, PasswordEncoder encoder, Clock clock,
                        @Value("${nitida.register.ip-limit:10}") int ipLimit) {
        this.users = users;
        this.encoder = encoder;
        this.clock = clock;
        this.ipLimiter = new RateLimiter(ipLimit, Duration.ofHours(1).toMillis(), clock);
    }

    @Transactional
    public Result execute(Command c) {
        ipLimiter.check(c.ip()); // before the Argon2id hash: that is the expensive part
        String email = Emails.normalize(c.email());
        String name = Texts.requireLength(c.displayName(), 2, 120, "INVALID_NAME", "El nombre debe tener entre 2 y 120 caracteres");
        PasswordPolicy.validate(c.password());
        if (users.findByEmail(email).isPresent()) {
            throw ApiException.conflict("EMAIL_TAKEN", "Ya existe una cuenta con ese correo");
        }
        var user = new AppUser(UuidV7.next(), email, encoder.encode(c.password()), name, false, clock.instant());
        users.save(user);
        return new Result(user.getId());
    }
}
