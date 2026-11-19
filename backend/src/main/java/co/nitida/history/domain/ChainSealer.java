package co.nitida.history.domain;

import co.nitida.shared.crypto.Sha256;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.LongFunction;

/** Builds the next link of a chain: hash_n = SHA-256(hash_{n-1} || JCS(publicContent_n)); hash_0 = 32 zero bytes. */
public final class ChainSealer {
    public static final byte[] GENESIS_HASH = new byte[32];

    public record Sealed(long seq, String canonicalPublicContent, byte[] previousHash, byte[] hash) {}

    private ChainSealer() {}

    public static byte[] hash(byte[] previousHash, String canonicalPublicContent) {
        return Sha256.digest(previousHash, canonicalPublicContent.getBytes(StandardCharsets.UTF_8));
    }

    /** The content factory receives the new seq (RN-61: consecutive, no gaps) and returns the public content. */
    public static Sealed seal(long lastSeq, byte[] lastHash, LongFunction<Map<String, Object>> publicContentForSeq) {
        long seq = lastSeq + 1;
        String canonical = JsonCanonicalizer.canonicalize(publicContentForSeq.apply(seq));
        return new Sealed(seq, canonical, lastHash, hash(lastHash, canonical));
    }
}
