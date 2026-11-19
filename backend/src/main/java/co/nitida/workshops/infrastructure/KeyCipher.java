package co.nitida.workshops.infrastructure;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** AES-256-GCM encryption of private signing keys under a KEK read from the environment (section 8.3). */
public class KeyCipher {
    public record Sealed(byte[] ciphertext, byte[] nonce) {}

    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec kek;
    private final int version;

    public KeyCipher(String kekBase64, int version) {
        byte[] raw = Base64.getDecoder().decode(kekBase64.strip());
        if (raw.length != 32) throw new IllegalStateException("The signing KEK must be 32 bytes (base64)");
        this.kek = new SecretKeySpec(raw, "AES");
        this.version = version;
    }

    public int version() { return version; }

    /** The workshop id is authenticated data: a key blob cannot be moved to another workshop. */
    public Sealed seal(byte[] plain, UUID workshopId) {
        try {
            byte[] nonce = new byte[12];
            RANDOM.nextBytes(nonce);
            var c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, kek, new GCMParameterSpec(128, nonce));
            c.updateAAD(aad(workshopId));
            return new Sealed(c.doFinal(plain), nonce);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("could not seal key", e);
        }
    }

    public byte[] open(byte[] ciphertext, byte[] nonce, UUID workshopId) {
        try {
            var c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, kek, new GCMParameterSpec(128, nonce));
            c.updateAAD(aad(workshopId));
            return c.doFinal(ciphertext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("could not open key", e);
        }
    }

    private static byte[] aad(UUID id) {
        return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
    }
}
