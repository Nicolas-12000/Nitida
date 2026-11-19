package co.nitida.shared.crypto;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** AES-256-GCM encryption of secrets at rest (signing keys, TOTP secrets) under a KEK read from the environment (section 8.3). */
public class KeyCipher {
    public record Sealed(byte[] ciphertext, byte[] nonce) {}

    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec kek;
    private final int version;
    private final SecretKeySpec previous;
    private final int previousVersion;

    public KeyCipher(String kekBase64, int version) {
        this(kekBase64, version, "", 0);
    }

    /** {@code previous} opens material sealed before a rotation. Empty means there is no previous key. */
    public KeyCipher(String kekBase64, int version, String previousBase64, int previousVersion) {
        this.kek = key(kekBase64);
        this.version = version;
        if (previousBase64 != null && !previousBase64.isBlank()) {
            if (previousVersion == version) throw new IllegalStateException("The previous KEK version must differ from the current one");
            this.previous = key(previousBase64);
            this.previousVersion = previousVersion;
        } else {
            this.previous = null;
            this.previousVersion = 0;
        }
    }

    private static SecretKeySpec key(String kekBase64) {
        byte[] raw = Base64.getDecoder().decode(kekBase64.strip());
        if (raw.length != 32) throw new IllegalStateException("The signing KEK must be 32 bytes (base64)");
        return new SecretKeySpec(raw, "AES");
    }

    public int version() { return version; }

    /** The owner id (workshop or user) is authenticated data: a blob cannot be moved to another owner. */
    public Sealed seal(byte[] plain, UUID ownerId) {
        try {
            byte[] nonce = new byte[12];
            RANDOM.nextBytes(nonce);
            var c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, kek, new GCMParameterSpec(128, nonce));
            c.updateAAD(aad(ownerId));
            return new Sealed(c.doFinal(plain), nonce);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("could not seal key", e);
        }
    }

    /** Tries the current KEK, then the previous one. For secrets that do not store a version (TOTP). */
    public byte[] open(byte[] ciphertext, byte[] nonce, UUID ownerId) {
        try {
            return decrypt(kek, ciphertext, nonce, ownerId);
        } catch (IllegalStateException e) {
            if (previous == null) throw e;
            return decrypt(previous, ciphertext, nonce, ownerId);
        }
    }

    /** Signing keys record {@code kek_version}. A version this process does not have fails closed. */
    public byte[] open(byte[] ciphertext, byte[] nonce, UUID ownerId, int sealedWith) {
        if (sealedWith == version) return decrypt(kek, ciphertext, nonce, ownerId);
        if (previous != null && sealedWith == previousVersion) return decrypt(previous, ciphertext, nonce, ownerId);
        throw new IllegalStateException("no KEK for version " + sealedWith);
    }

    private static byte[] decrypt(SecretKeySpec key, byte[] ciphertext, byte[] nonce, UUID ownerId) {
        try {
            var c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            c.updateAAD(aad(ownerId));
            return c.doFinal(ciphertext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("could not open key", e);
        }
    }

    private static byte[] aad(UUID id) {
        return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
    }
}
