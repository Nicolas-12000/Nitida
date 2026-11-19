package co.nitida.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.nitida.shared.crypto.Ed25519;
import co.nitida.shared.crypto.SecureTokens;
import co.nitida.shared.crypto.Sha256;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CryptoAndLimiterTest {

    @Test
    void tokensAreUnique22CharBase64UrlAndOnlyHashIsStable() {
        var seen = new HashSet<String>();
        for (int i = 0; i < 100_000; i++) {
            String t = SecureTokens.newToken();
            assertThat(t).hasSize(22).matches("[A-Za-z0-9_-]{22}");
            assertThat(seen.add(t)).isTrue();
        }
        String t = SecureTokens.newToken();
        assertThat(SecureTokens.hash(t)).isEqualTo(SecureTokens.hash(t)).isNotEqualTo(t).hasSize(64);
    }

    @Test
    void claimCodesAvoidAmbiguousCharacters() {
        for (int i = 0; i < 2000; i++) assertThat(SecureTokens.newCode(8)).hasSize(8).doesNotContain("0", "O", "1", "I", "L");
    }

    @Test
    void sha256MatchesKnownVector() {
        assertThat(Sha256.hexOf("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void ed25519SignsAndVerifiesAndFailsClosed() {
        var pair = Ed25519.generate();
        byte[] data = "hello".getBytes();
        byte[] sig = Ed25519.sign(pair.getPrivate().getEncoded(), data);
        assertThat(Ed25519.verify(pair.getPublic().getEncoded(), data, sig)).isTrue();
        assertThat(Ed25519.verify(pair.getPublic().getEncoded(), "hellp".getBytes(), sig)).isFalse();
        assertThat(Ed25519.verify(new byte[] {1, 2, 3}, data, sig)).isFalse();
        assertThat(Ed25519.verify(pair.getPublic().getEncoded(), data, new byte[] {1})).isFalse();
    }

    @Test
    void limiterBlocksAfterBudgetAndRecoversAfterWindow() {
        var clock = new MutableClock();
        var limiter = new RateLimiter(3, 1000, clock);
        for (int i = 0; i < 3; i++) limiter.check("k");
        assertThatThrownBy(() -> limiter.check("k")).isInstanceOf(ApiException.class);
        limiter.check("other");
        clock.advance(Duration.ofMillis(1000));
        limiter.check("k");
    }

    static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration d) { now = now.plus(d); }

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void keyCipherRoundTripsAndBindsToWorkshop() {
        var cipher = new co.nitida.shared.crypto.KeyCipher("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", 1);
        var ws = UUID.randomUUID();
        var sealed = cipher.seal("secret".getBytes(), ws);
        assertThat(cipher.open(sealed.ciphertext(), sealed.nonce(), ws)).isEqualTo("secret".getBytes());
        assertThatThrownBy(() -> cipher.open(sealed.ciphertext(), sealed.nonce(), UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
    }
}
