package co.nitida.identity.application;

import co.nitida.identity.domain.PasswordReset;
import co.nitida.identity.domain.PasswordResetRepository;
import co.nitida.shared.Emails;
import co.nitida.shared.RateLimiter;
import co.nitida.shared.UuidV7;
import co.nitida.shared.crypto.SecureTokens;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Always looks the same to the caller: an unknown address gets no mail and the same answer. */
@Service
public class RequestPasswordResetUseCase {
    private final co.nitida.identity.domain.AppUserRepository users;
    private final PasswordResetRepository resets;
    private final Mailbox mailbox;
    private final Clock clock;
    private final RateLimiter ipLimiter;

    RequestPasswordResetUseCase(co.nitida.identity.domain.AppUserRepository users, PasswordResetRepository resets, Mailbox mailbox, Clock clock,
                                @Value("${nitida.password-reset.ip-limit:10}") int ipLimit) {
        this.users = users;
        this.resets = resets;
        this.mailbox = mailbox;
        this.clock = clock;
        this.ipLimiter = new RateLimiter(ipLimit, Duration.ofHours(1).toMillis(), clock);
    }

    @Transactional
    public void execute(String email, String ip) {
        ipLimiter.check(ip == null ? "" : ip);
        var user = users.findByEmail(Emails.normalize(email)).orElse(null);
        if (user == null) return;
        String token = SecureTokens.newToken();
        resets.save(new PasswordReset(UuidV7.next(), user.getId(), SecureTokens.hash(token), clock.instant()));
        mailbox.send(user.getEmail(), "Recuperar la contraseña de Nítida",
                "Recibimos una solicitud para cambiar la contraseña. El código vence en una hora y sirve una sola vez.\n\n" + token + "\n");
    }
}
