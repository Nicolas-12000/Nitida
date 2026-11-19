package co.nitida.history;

import static org.assertj.core.api.Assertions.assertThat;

import co.nitida.history.domain.ChainSealer;
import co.nitida.history.domain.ChainVerifier;
import co.nitida.history.domain.ChainVerifier.Head;
import co.nitida.history.domain.ChainVerifier.Link;
import co.nitida.shared.crypto.Ed25519;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

class ChainTest {
    static final KeyPair KEY = Ed25519.generate();
    static final UUID KEY_ID = UUID.randomUUID();

    record Built(UUID motorcycleId, List<Link> links, Head head) {}

    static Built build(List<String> diagnoses) {
        UUID moto = UUID.randomUUID();
        var links = new ArrayList<Link>();
        long lastSeq = 0;
        byte[] lastHash = ChainSealer.GENESIS_HASH;
        for (String d : diagnoses) {
            var sealed = ChainSealer.seal(lastSeq, lastHash, seq -> {
                var m = new LinkedHashMap<String, Object>();
                m.put("motorcycleId", moto.toString());
                m.put("seq", seq);
                m.put("diagnosis", d);
                return m;
            });
            byte[] sig = Ed25519.sign(KEY.getPrivate().getEncoded(), sealed.hash());
            links.add(new Link(moto, sealed.seq(), sealed.canonicalPublicContent(), sealed.previousHash(), sealed.hash(), sig, KEY_ID));
            lastSeq = sealed.seq();
            lastHash = sealed.hash();
        }
        return new Built(moto, links, new Head(lastSeq, lastHash));
    }

    static VerificationResult verify(Built b) {
        return ChainVerifier.verify(b.motorcycleId(), b.links(), b.head(), id -> id.equals(KEY_ID) ? Optional.of(KEY.getPublic().getEncoded()) : Optional.empty());
    }

    @Provide
    Arbitrary<List<String>> diagnoses() {
        return Arbitraries.strings().ofMinLength(1).ofMaxLength(30).list().ofMinSize(1).ofMaxSize(8);
    }

    /** P2: any list of valid contents builds a chain that verifies as VALID. */
    @Property(tries = 60)
    void anyBuiltChainIsValid(@ForAll("diagnoses") List<String> d) {
        assertThat(verify(build(d)).status()).isEqualTo(VerificationResult.Status.VALID);
    }

    /** P3: changing any byte of any entry (content, previous hash, hash or signature) is INVALID at seq <= that entry. */
    @Property(tries = 150)
    void tamperingAnyByteIsDetectedAtOrBeforeTheEntry(@ForAll("diagnoses") List<String> d, @ForAll @IntRange(min = 0, max = 7) int entryIdx,
                                                      @ForAll @IntRange(min = 0, max = 3) int field, @ForAll int byteIdx) {
        var built = build(d);
        int k = entryIdx % built.links().size();
        Link original = built.links().get(k);
        Link altered = switch (field) {
            case 0 -> new Link(original.motorcycleId(), original.seq(), flipChar(original.publicContent(), byteIdx), original.previousHash(), original.hash(), original.signature(), original.keyId());
            case 1 -> new Link(original.motorcycleId(), original.seq(), original.publicContent(), flip(original.previousHash(), byteIdx), original.hash(), original.signature(), original.keyId());
            case 2 -> new Link(original.motorcycleId(), original.seq(), original.publicContent(), original.previousHash(), flip(original.hash(), byteIdx), original.signature(), original.keyId());
            default -> new Link(original.motorcycleId(), original.seq(), original.publicContent(), original.previousHash(), original.hash(), flip(original.signature(), byteIdx), original.keyId());
        };
        var links = new ArrayList<>(built.links());
        links.set(k, altered);
        var result = verify(new Built(built.motorcycleId(), links, built.head()));
        assertThat(result.status()).isEqualTo(VerificationResult.Status.INVALID);
        assertThat(result.seq()).isLessThanOrEqualTo(original.seq());
    }

    static byte[] flip(byte[] in, int idx) {
        byte[] out = in.clone();
        out[Math.floorMod(idx, out.length)] ^= 0x01;
        return out;
    }

    static String flipChar(String s, int idx) {
        char[] c = s.toCharArray();
        int i = Math.floorMod(idx, c.length);
        c[i] = (char) (c[i] == 'x' ? 'y' : 'x');
        return new String(c);
    }

    @Test
    void emptyChainWithoutHeadIsValid() {
        assertThat(ChainVerifier.verify(UUID.randomUUID(), List.of(), null, id -> Optional.empty()).isValid()).isTrue();
    }

    @Test
    void unknownSigningKeyIsUnverifiableNeverValid() {
        var b = build(List.of("uno", "dos"));
        var r = ChainVerifier.verify(b.motorcycleId(), b.links(), b.head(), id -> Optional.empty());
        assertThat(r.status()).isEqualTo(VerificationResult.Status.UNVERIFIABLE);
    }

    @Test
    void keyLookupFailureIsUnverifiable() {
        var b = build(List.of("uno"));
        var r = ChainVerifier.verify(b.motorcycleId(), b.links(), b.head(), id -> { throw new IllegalStateException("db down"); });
        assertThat(r.status()).isEqualTo(VerificationResult.Status.UNVERIFIABLE);
    }

    @Test
    void deletingTheLastEntriesIsDetectedThroughTheHead() {
        var b = build(List.of("uno", "dos", "tres"));
        var truncated = new Built(b.motorcycleId(), b.links().subList(0, 2), b.head());
        var r = verify(truncated);
        assertThat(r.status()).isEqualTo(VerificationResult.Status.INVALID);
        assertThat(r.seq()).isEqualTo(3);
    }

    @Test
    void deletingAMiddleEntryBreaksTheSequence() {
        var b = build(List.of("uno", "dos", "tres"));
        var links = new ArrayList<>(b.links());
        links.remove(1);
        var r = verify(new Built(b.motorcycleId(), links, b.head()));
        assertThat(r.status()).isEqualTo(VerificationResult.Status.INVALID);
        assertThat(r.seq()).isEqualTo(2);
    }

    @Test
    void entryMovedToAnotherMotorcycleIsRejected() {
        var b = build(List.of("uno"));
        var r = ChainVerifier.verify(UUID.randomUUID(), b.links(), b.head(), id -> Optional.of(KEY.getPublic().getEncoded()));
        assertThat(r.status()).isEqualTo(VerificationResult.Status.INVALID);
    }

    @Test
    void malformedContentIsNeverValid() {
        var b = build(List.of("uno"));
        var l = b.links().get(0);
        var bad = new Link(l.motorcycleId(), l.seq(), "{not json", l.previousHash(), ChainSealer.hash(l.previousHash(), "{not json"), l.signature(), l.keyId());
        var r = verify(new Built(b.motorcycleId(), List.of(bad), b.head()));
        assertThat(r.isValid()).isFalse();
    }

    @Test
    void recordsCompareHashesByContent() {
        var b = build(List.of("uno"));
        var link = b.links().get(0);
        var sameLink = new Link(link.motorcycleId(), link.seq(), link.publicContent(),
                link.previousHash().clone(), link.hash().clone(), link.signature().clone(), link.keyId());
        assertThat(sameLink).isEqualTo(link);
        assertThat(sameLink.hashCode()).isEqualTo(link.hashCode());
        assertThat(link.toString()).contains(link.motorcycleId().toString());
        assertThat(link).isNotEqualTo("x");
        assertThat(new Link(UUID.randomUUID(), link.seq(), link.publicContent(), link.previousHash(), link.hash(), link.signature(), link.keyId())).isNotEqualTo(link);
        assertThat(new Link(link.motorcycleId(), 9, link.publicContent(), link.previousHash(), link.hash(), link.signature(), link.keyId())).isNotEqualTo(link);
        assertThat(new Link(link.motorcycleId(), link.seq(), "otro", link.previousHash(), link.hash(), link.signature(), link.keyId())).isNotEqualTo(link);
        assertThat(new Link(link.motorcycleId(), link.seq(), link.publicContent(), new byte[] {1}, link.hash(), link.signature(), link.keyId())).isNotEqualTo(link);
        assertThat(new Link(link.motorcycleId(), link.seq(), link.publicContent(), link.previousHash(), new byte[] {1}, link.signature(), link.keyId())).isNotEqualTo(link);
        assertThat(new Link(link.motorcycleId(), link.seq(), link.publicContent(), link.previousHash(), link.hash(), new byte[] {1}, link.keyId())).isNotEqualTo(link);
        assertThat(new Link(link.motorcycleId(), link.seq(), link.publicContent(), link.previousHash(), link.hash(), link.signature(), UUID.randomUUID())).isNotEqualTo(link);

        var head = b.head();
        var sameHead = new Head(head.lastSeq(), head.lastHash().clone());
        assertThat(sameHead).isEqualTo(head);
        assertThat(sameHead.hashCode()).isEqualTo(head.hashCode());
        assertThat(head.toString()).contains("Head");
        assertThat(head).isNotEqualTo("x");
        assertThat(new Head(0, head.lastHash())).isNotEqualTo(head);
        assertThat(new Head(head.lastSeq(), new byte[] {1})).isNotEqualTo(head);

        var sealed = ChainSealer.seal(0, ChainSealer.GENESIS_HASH, seq -> java.util.Map.of("seq", seq));
        var sameSealed = new ChainSealer.Sealed(sealed.seq(), sealed.canonicalPublicContent(), sealed.previousHash().clone(), sealed.hash().clone());
        assertThat(sameSealed).isEqualTo(sealed);
        assertThat(sameSealed.hashCode()).isEqualTo(sealed.hashCode());
        assertThat(sealed.toString()).contains("Sealed");
        assertThat(sealed).isNotEqualTo("x");
        assertThat(new ChainSealer.Sealed(9, sealed.canonicalPublicContent(), sealed.previousHash(), sealed.hash())).isNotEqualTo(sealed);
        assertThat(new ChainSealer.Sealed(sealed.seq(), "otro", sealed.previousHash(), sealed.hash())).isNotEqualTo(sealed);
        assertThat(new ChainSealer.Sealed(sealed.seq(), sealed.canonicalPublicContent(), new byte[] {1}, sealed.hash())).isNotEqualTo(sealed);
        assertThat(new ChainSealer.Sealed(sealed.seq(), sealed.canonicalPublicContent(), sealed.previousHash(), new byte[] {1})).isNotEqualTo(sealed);

        var missingMotorcycle = ChainVerifier.verify(null, b.links(), b.head(), id -> Optional.of(KEY.getPublic().getEncoded()));
        assertThat(missingMotorcycle.status()).isEqualTo(VerificationResult.Status.INVALID);
    }

    @Test
    void sealIncrementsSeqAndLinksToThePreviousHash() {
        var first = ChainSealer.seal(0, ChainSealer.GENESIS_HASH, seq -> java.util.Map.of("seq", seq));
        var second = ChainSealer.seal(first.seq(), first.hash(), seq -> java.util.Map.of("seq", seq));
        assertThat(first.seq()).isEqualTo(1);
        assertThat(second.seq()).isEqualTo(2);
        assertThat(second.previousHash()).isEqualTo(first.hash());
        assertThat(first.previousHash()).isEqualTo(new byte[32]);
    }
}
