package co.nitida.identity.domain;

import java.io.ByteArrayOutputStream;
import java.util.Locale;

/** RFC 4648 Base32, the alphabet authenticator apps expect for TOTP secrets. Encoded without padding. */
public final class Base32 {
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Base32() {}

    public static String encode(byte[] data) {
        var out = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                out.append(ALPHABET.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) out.append(ALPHABET.charAt((buffer << (5 - bits)) & 31));
        return out.toString();
    }

    /** Tolerant like the apps: case, spaces and padding are ignored. */
    public static byte[] decode(String text) {
        var out = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char c : text.toUpperCase(Locale.ROOT).toCharArray()) {
            if (c == '=' || c == ' ' || c == '-') continue;
            int value = ALPHABET.indexOf(c);
            if (value < 0) throw new IllegalArgumentException("not a Base32 character");
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }
}
