package co.nitida.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.nitida.history.domain.PublicLink;
import co.nitida.shared.ApiException;
import co.nitida.shared.UuidV7;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** RN-72: 1..365 days (30 by default), valid while now < expiresAt, revocable. */
class PublicLinkTest {
    static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private static PublicLink link(Integer days) {
        return new PublicLink(UuidV7.next(), UuidV7.next(), UuidV7.next(), UuidV7.next(), "hash", days, NOW);
    }

    @Test
    void defaultsToThirtyDays() {
        assertThat(link(null).getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
    }

    @ParameterizedTest
    @CsvSource({"0,false", "1,true", "365,true", "366,false", "-5,false"})
    void expirationBoundaries(int days, boolean valid) {
        if (valid) assertThat(link(days).getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(days)));
        else assertThatThrownBy(() -> link(days)).satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("INVALID_EXPIRATION"));
    }

    @Test
    void validOnlyBeforeTheExpirationInstantAndWhileNotRevoked() {
        var l = link(1);
        var expires = l.getExpiresAt();
        assertThat(l.isValidAt(expires.minusMillis(1))).isTrue();
        assertThat(l.isValidAt(expires)).isFalse();
        assertThat(l.isValidAt(expires.plusSeconds(1))).isFalse();

        l.revoke(NOW.plusSeconds(10));
        assertThat(l.isValidAt(NOW.plusSeconds(11))).isFalse();
        l.revoke(NOW.plusSeconds(99)); // revoking twice keeps the first moment and never throws
        assertThat(l.isValidAt(NOW)).isFalse();
    }
}
