package co.nitida.intake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.nitida.intake.domain.DiscardReason;
import co.nitida.intake.domain.Intake;
import co.nitida.intake.domain.IntakeStatus;
import co.nitida.shared.ApiException;
import co.nitida.shared.UuidV7;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IntakeDomainTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private static Intake open() {
        return Intake.register(UuidV7.next(), UuidV7.next(), UuidV7.next(), 1000, UuidV7.next(), UuidV7.next(), UuidV7.next(), NOW);
    }

    @Test
    void bothPhotosAreRequired() {
        UUID id = UuidV7.next();
        assertThatThrownBy(() -> Intake.register(id, id, id, 10, null, id, id, NOW)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Intake.register(id, id, id, 10, id, null, id, NOW)).isInstanceOf(ApiException.class);
    }

    @Test
    void discardClosesOnceAndNeedsAReason() {
        var i = open();
        assertThat(i.getStatus()).isEqualTo(IntakeStatus.OPEN);
        assertThatThrownBy(() -> i.discard(null)).isInstanceOf(ApiException.class);
        i.discard(DiscardReason.ENTRY_ERROR);
        assertThat(i.getStatus()).isEqualTo(IntakeStatus.DISCARDED);
        assertThat(i.getDiscardReason()).isEqualTo(DiscardReason.ENTRY_ERROR);
        assertThatThrownBy(() -> i.discard(DiscardReason.ENTRY_ERROR)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> i.linkToOrder(UuidV7.next())).isInstanceOf(ApiException.class);
    }

    @Test
    void linkingToAnOrderClosesIt() {
        var i = open();
        UUID order = UuidV7.next();
        i.linkToOrder(order);
        assertThat(i.getStatus()).isEqualTo(IntakeStatus.LINKED);
        assertThat(i.getWorkOrderId()).isEqualTo(order);
        assertThatThrownBy(() -> i.discard(DiscardReason.CUSTOMER_WITHDREW)).isInstanceOf(ApiException.class);
    }
}
