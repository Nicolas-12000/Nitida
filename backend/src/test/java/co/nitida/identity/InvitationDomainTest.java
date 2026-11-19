package co.nitida.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.nitida.identity.domain.Invitation;
import co.nitida.shared.ApiException;
import co.nitida.shared.Role;
import co.nitida.shared.UuidV7;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InvitationDomainTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private static Invitation invitation() {
        return new Invitation(UuidV7.next(), UuidV7.next(), "mec@taller.co", Role.MECHANIC, "hash", UuidV7.next(), NOW);
    }

    @Test
    void validForSeventyTwoHoursExactly() {
        var i = invitation();
        assertThat(i.getExpiresAt()).isEqualTo(NOW.plusSeconds(72 * 3600));
        i.accept("mec@taller.co", i.getExpiresAt().minusMillis(1));
        assertThat(i.getUsedAt()).isNotNull();
    }

    @Test
    void expiresAtTheExactInstant() {
        var i = invitation();
        assertThatThrownBy(() -> i.accept("mec@taller.co", i.getExpiresAt())).isInstanceOf(ApiException.class);
    }

    @Test
    void isSingleUseAndEmailBound() {
        var i = invitation();
        assertThatThrownBy(() -> i.accept("otro@taller.co", NOW)).isInstanceOf(ApiException.class);
        i.accept("MEC@Taller.co", NOW); // case-insensitive
        assertThatThrownBy(() -> i.accept("mec@taller.co", NOW)).isInstanceOf(ApiException.class);
    }
}
