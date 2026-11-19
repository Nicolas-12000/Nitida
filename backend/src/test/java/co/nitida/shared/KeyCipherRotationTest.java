package co.nitida.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.nitida.shared.crypto.KeyCipher;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** A key sealed under version 1 still opens after the process moves to version 2 and keeps the old KEK. */
class KeyCipherRotationTest {
    private static String kek(byte fill) {
        return Base64.getEncoder().encodeToString(new byte[] {fill, fill, fill, fill, fill, fill, fill, fill,
                fill, fill, fill, fill, fill, fill, fill, fill, fill, fill, fill, fill, fill, fill, fill, fill,
                fill, fill, fill, fill, fill, fill, fill, fill});
    }

    @Test
    void materialSealedWithThePreviousKekStillOpensAndAnUnknownVersionFailsClosed() {
        UUID owner = UUID.randomUUID();
        byte[] secret = "llave-de-firma".getBytes();
        var v1 = new KeyCipher(kek((byte) 1), 1);
        var sealed = v1.seal(secret, owner);

        var rotated = new KeyCipher(kek((byte) 2), 2, kek((byte) 1), 1);
        assertThat(rotated.open(sealed.ciphertext(), sealed.nonce(), owner, 1)).isEqualTo(secret);
        assertThat(rotated.open(sealed.ciphertext(), sealed.nonce(), owner)).isEqualTo(secret);

        var fresh = rotated.seal(secret, owner);
        assertThat(rotated.open(fresh.ciphertext(), fresh.nonce(), owner, 2)).isEqualTo(secret);
        assertThatThrownBy(() -> rotated.open(sealed.ciphertext(), sealed.nonce(), owner, 9))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no KEK");
        assertThatThrownBy(() -> new KeyCipher(kek((byte) 3), 3).open(sealed.ciphertext(), sealed.nonce(), owner, 1))
                .isInstanceOf(IllegalStateException.class);
    }
}
