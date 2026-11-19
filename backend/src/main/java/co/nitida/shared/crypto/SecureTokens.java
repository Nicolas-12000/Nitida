package co.nitida.shared.crypto;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Random tokens (128 bits, base64url without padding = 22 chars) and their stored hashes (RN-71). */
public final class SecureTokens {
    private static final SecureRandom RANDOM = new SecureRandom();
    /** No 0/O/1/I/L so a code read aloud or from a receipt is unambiguous (RN-80). */
    private static final char[] CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();

    private SecureTokens() {}

    public static String newToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String newCode(int length) {
        var sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append(CODE_ALPHABET[RANDOM.nextInt(CODE_ALPHABET.length)]);
        return sb.toString();
    }

    public static byte[] randomBytes(int n) {
        byte[] bytes = new byte[n];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    /** Only this value is persisted; the plain token is shown once. */
    public static String hash(String token) {
        return HexFormat.of().formatHex(Sha256.digest(token));
    }
}
