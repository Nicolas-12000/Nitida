package co.nitida.shared.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class Sha256 {
    private Sha256() {}

    public static byte[] digest(byte[]... parts) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            for (byte[] p : parts) md.update(p);
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static byte[] digest(String text) {
        return digest(text.getBytes(StandardCharsets.UTF_8));
    }

    public static String hex(byte[] data) {
        return HexFormat.of().formatHex(digest(data));
    }

    public static String hexOf(String text) {
        return HexFormat.of().formatHex(digest(text));
    }
}
