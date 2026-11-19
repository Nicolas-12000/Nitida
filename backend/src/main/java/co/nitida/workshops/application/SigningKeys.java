package co.nitida.workshops.application;

import co.nitida.shared.UuidV7;
import co.nitida.shared.crypto.Ed25519;
import co.nitida.shared.crypto.KeyCipher;
import co.nitida.workshops.domain.SigningKey;
import co.nitida.workshops.domain.SigningKeyRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Creates an Ed25519 key whose private half is stored sealed with the KEK (section 8.3). */
@Component
class SigningKeys {
    private final SigningKeyRepository keys;
    private final KeyCipher cipher;

    SigningKeys(SigningKeyRepository keys, KeyCipher cipher) {
        this.keys = keys;
        this.cipher = cipher;
    }

    void create(UUID workshopId, Instant now) {
        var pair = Ed25519.generate();
        var sealed = cipher.seal(pair.getPrivate().getEncoded(), workshopId);
        keys.save(new SigningKey(UuidV7.next(), workshopId, pair.getPublic().getEncoded(), sealed.ciphertext(), sealed.nonce(),
                cipher.version(), now));
    }

    boolean exists(UUID workshopId) {
        return keys.findFirstByWorkshopIdAndRetiredAtIsNullOrderByCreatedAtDesc(workshopId).isPresent();
    }
}
