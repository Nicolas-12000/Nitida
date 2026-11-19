package co.nitida.history.domain;

import co.nitida.history.VerificationResult;
import co.nitida.shared.Json;
import co.nitida.shared.crypto.Ed25519;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Recomputes hashes and signatures in order. Fail closed (section 8.3): a missing key, malformed data or any
 * unexpected exception yields UNVERIFIABLE, never VALID.
 */
public final class ChainVerifier {
    /** What is needed from a stored entry; keeps the verifier free of JPA. */
    public record Link(UUID motorcycleId, long seq, String publicContent, byte[] previousHash, byte[] hash, byte[] signature, UUID keyId) {
        @Override public boolean equals(Object other) {
            return other instanceof Link link
                    && seq == link.seq
                    && java.util.Objects.equals(motorcycleId, link.motorcycleId)
                    && java.util.Objects.equals(publicContent, link.publicContent)
                    && java.util.Objects.equals(keyId, link.keyId)
                    && java.util.Arrays.equals(previousHash, link.previousHash)
                    && java.util.Arrays.equals(hash, link.hash)
                    && java.util.Arrays.equals(signature, link.signature);
        }

        @Override public int hashCode() {
            return java.util.Objects.hash(motorcycleId, seq, publicContent, keyId,
                    java.util.Arrays.hashCode(previousHash), java.util.Arrays.hashCode(hash), java.util.Arrays.hashCode(signature));
        }

        @Override public String toString() {
            return "Link[motorcycleId=" + motorcycleId + ", seq=" + seq + ", publicContent=" + publicContent
                    + ", previousHash=" + java.util.Arrays.toString(previousHash)
                    + ", hash=" + java.util.Arrays.toString(hash)
                    + ", signature=" + java.util.Arrays.toString(signature) + ", keyId=" + keyId + "]";
        }
    }

    /** The persisted head: where the chain is supposed to end. */
    public record Head(long lastSeq, byte[] lastHash) {
        @Override public boolean equals(Object other) {
            return other instanceof Head head && lastSeq == head.lastSeq && java.util.Arrays.equals(lastHash, head.lastHash);
        }

        @Override public int hashCode() {
            return java.util.Objects.hash(lastSeq, java.util.Arrays.hashCode(lastHash));
        }

        @Override public String toString() {
            return "Head[lastSeq=" + lastSeq + ", lastHash=" + java.util.Arrays.toString(lastHash) + "]";
        }
    }

    private ChainVerifier() {}

    public static VerificationResult verify(UUID motorcycleId, List<Link> links, Head head, Function<UUID, Optional<byte[]>> publicKeys) {
        try {
            byte[] previous = ChainSealer.GENESIS_HASH;
            long expectedSeq = 1;
            for (Link link : links) {
                if (link.seq() != expectedSeq) return VerificationResult.invalid(expectedSeq, "missing or repeated sequence number");
                if (!MessageDigest.isEqual(link.previousHash(), previous)) return VerificationResult.invalid(link.seq(), "broken link to the previous entry");
                byte[] recomputed = ChainSealer.hash(previous, link.publicContent());
                if (!MessageDigest.isEqual(recomputed, link.hash())) return VerificationResult.invalid(link.seq(), "content does not match its hash");
                if (!contentMatchesRow(motorcycleId, link)) return VerificationResult.invalid(link.seq(), "content does not belong to this entry");
                Optional<byte[]> key = publicKeys.apply(link.keyId());
                if (key.isEmpty()) return VerificationResult.unverifiable("signing key not found for entry " + link.seq());
                if (!Ed25519.verify(key.get(), link.hash(), link.signature())) return VerificationResult.invalid(link.seq(), "invalid signature");
                previous = link.hash();
                expectedSeq++;
            }
            if (head == null) {
                return links.isEmpty() ? VerificationResult.valid() : VerificationResult.invalid(1, "chain head is missing");
            }
            if (head.lastSeq() != expectedSeq - 1 || !MessageDigest.isEqual(head.lastHash(), previous)) {
                return VerificationResult.invalid(expectedSeq, "entries are missing: the chain head points further");
            }
            return VerificationResult.valid();
        } catch (RuntimeException e) {
            return VerificationResult.unverifiable("verification could not be completed");
        }
    }

    /** The canonical text must parse, re-canonicalize to itself, and carry this motorcycle and sequence number. */
    private static boolean contentMatchesRow(UUID motorcycleId, Link link) {
        Object parsed = Json.mapper().readValue(link.publicContent(), Object.class);
        if (!JsonCanonicalizer.canonicalize(parsed).equals(link.publicContent())) return false;
        if (motorcycleId == null || !(parsed instanceof java.util.Map<?, ?> map)) return false;
        return motorcycleId.toString().equals(map.get("motorcycleId"))
                && map.get("seq") instanceof Number n && n.longValue() == link.seq();
    }
}
