package co.nitida.identity.application;

import co.nitida.identity.domain.AppUserRepository;
import co.nitida.identity.domain.PasswordPolicy;
import co.nitida.identity.domain.PasswordResetRepository;
import co.nitida.shared.ApiException;
import co.nitida.shared.crypto.SecureTokens;
import java.time.Clock;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResetPasswordUseCase {
    private final PasswordResetRepository resets;
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final Clock clock;

    ResetPasswordUseCase(PasswordResetRepository resets, AppUserRepository users, PasswordEncoder encoder, Clock clock) {
        this.resets = resets;
        this.users = users;
        this.encoder = encoder;
        this.clock = clock;
    }

    @Transactional
    public void execute(String token, String newPassword) {
        if (token == null || token.isBlank()) throw ApiException.notFound();
        PasswordPolicy.validate(newPassword);
        var reset = resets.findByTokenHash(SecureTokens.hash(token.strip())).orElseThrow(ApiException::notFound);
        var now = clock.instant();
        reset.consume(now);
        var user = users.findById(reset.getUserId()).orElseThrow(ApiException::notFound);
        user.changePassword(encoder.encode(newPassword));
        user.registerSuccess();
    }
}
