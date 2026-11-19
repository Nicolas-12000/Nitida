package co.nitida.history.domain;

import co.nitida.shared.crypto.Sha256;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.LongFunction;

/** Builds the next link of a chain: hash_n = SHA-256(hash_{n-1} || JCS(publicContent_n)); hash_0 = 32 zero bytes. */
public final class ChainSealer {
    public static final byte[] GENESIS_HASH = new byte[32];

    public record Sealed(long seq, String canonicalPublicContent, byte[] previousHash, byte[] hash) {
        @Override public boolean equals(Object other) {
            return other instanceof Sealed sealed
                    && seq == sealed.seq
                    && java.util.Objects.equals(canonicalPublicContent, sealed.canonicalPublicContent)
                    && java.util.Arrays.equals(previousHash, sealed.previousHash)
                    && java.util.Arrays.equals(hash, sealed.hash);
        }

        @Override public int hashCode() {
            return java.util.Objects.hash(seq, canonicalPublicContent, java.util.Arrays.hashCode(previousHash), java.util.Arrays.hashCode(hash));
        }

        @Override public String toString() {
            return "Sealed[seq=" + seq + ", canonicalPublicContent=" + canonicalPublicContent
                    + ", previousHash=" + java.util.Arrays.toString(previousHash)
                    + ", hash=" + java.util.Arrays.toString(hash) + "]";
        }
    }

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
