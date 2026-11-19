package co.nitida.identity.domain;

import co.nitida.shared.crypto.SecureTokens;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * RFC 6238 time-based one-time passwords (HMAC-SHA1, 30 s steps, 6 digits: what every authenticator app does).
 * A code is accepted for the current step and one step either side (clock drift), and never twice: the caller keeps
 * the last accepted step and passes it back.
 */
public final class Totp {
    public static final int STEP_SECONDS = 30;
    private static final int DIGITS = 6;
    private static final int[] POWERS = {1, 10, 100, 1_000, 10_000, 100_000, 1_000_000};

    private Totp() {}

    public static byte[] newSecret() {
        return SecureTokens.randomBytes(20); // 160 bits, the size RFC 4226 recommends
    }

    public static long stepAt(long epochSeconds) {
        return epochSeconds / STEP_SECONDS;
    }

    public static String code(byte[] secret, long step) {
        try {
            var mac = Mac.getInstance("HmacSHA1"); // NOSONAR java:S4790 — RFC 6238 authenticators verify HMAC-SHA1
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0F; // dynamic truncation (RFC 4226 section 5.3)
            int binary = ((hash[offset] & 0x7F) << 24) | ((hash[offset + 1] & 0xFF) << 16) | ((hash[offset + 2] & 0xFF) << 8) | (hash[offset + 3] & 0xFF);
            return String.format("%0" + DIGITS + "d", binary % POWERS[DIGITS]);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 unavailable", e);
        }
    }

    /** The step the code belongs to, when it is valid now and newer than {@code lastAcceptedStep}; empty otherwise. */
    public static OptionalLong verify(byte[] secret, String candidate, long epochSeconds, long lastAcceptedStep) {
        if (candidate == null || !candidate.matches("\\d{" + DIGITS + "}")) return OptionalLong.empty();
        byte[] given = candidate.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        long now = stepAt(epochSeconds);
        long matched = -1;
        for (long step = now - 1; step <= now + 1; step++) { // all three are always computed: no early exit to time
            boolean equal = MessageDigest.isEqual(code(secret, step).getBytes(java.nio.charset.StandardCharsets.US_ASCII), given);
            if (equal && step > lastAcceptedStep) matched = step;
        }
        return matched < 0 ? OptionalLong.empty() : OptionalLong.of(matched);
    }
}
