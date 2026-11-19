package co.nitida.identity.application;

import co.nitida.identity.domain.AppUserRepository;
import co.nitida.identity.domain.Base32;
import co.nitida.identity.domain.Totp;
import co.nitida.shared.ApiException;
import co.nitida.shared.audit.AuditService;
import co.nitida.shared.crypto.KeyCipher;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Step one of enrolling TOTP: a new secret, sealed at rest, shown once as text and as an otpauth link for the QR. */
@Service
public class SetupTotpUseCase {
    /** The secret leaves the server only here, to the person who is enrolling. */
    public record Result(String secret, String otpauthUri) {}

    private final AppUserRepository users;
    private final KeyCipher cipher;
    private final AuditService audit;

    SetupTotpUseCase(AppUserRepository users, KeyCipher cipher, AuditService audit) {
        this.users = users;
        this.cipher = cipher;
        this.audit = audit;
    }

    @Transactional
    public Result execute(UUID userId) {
        var user = users.findById(userId).orElseThrow(ApiException::forbidden);
        byte[] secret = Totp.newSecret();
        var sealed = cipher.seal(secret, userId);
        user.startTotpEnrollment(sealed.ciphertext(), sealed.nonce()); // 409 if it is already active
        audit.record(null, userId, "TOTP_ENROLLMENT_STARTED", "app_user", userId, null);
        String base32 = Base32.encode(secret);
        String account = URLEncoder.encode(user.getEmail(), StandardCharsets.UTF_8);
        return new Result(base32, "otpauth://totp/Nitida:" + account + "?secret=" + base32 + "&issuer=Nitida&algorithm=SHA1&digits=6&period=30");
    }
}
