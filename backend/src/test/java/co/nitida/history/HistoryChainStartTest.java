package co.nitida.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.nitida.history.application.RecordHistoryEntryUseCase;
import co.nitida.motorcycles.MotorcycleRegistered;
import co.nitida.shared.UuidV7;
import co.nitida.shared.events.OrderDelivered;
import co.nitida.support.IntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * A delivery that arrives before INITIAL_REGISTRATION fails and is safe to retry once the chain exists.
 * The publication registry is what retries it (EventResubmission); this is the case it has to survive.
 */
class HistoryChainStartTest extends IntegrationTest {
    @Autowired RecordHistoryEntryUseCase history;

    @Test
    void aDeliveryBeforeTheChainStartsFailsAndSucceedsOnRetry() throws Exception {
        var owner = registerAndLogin();
        UUID workshop = createWorkshop(owner);
        UUID motorcycle = UuidV7.next();
        var delivered = new OrderDelivered(UuidV7.next(), workshop, motorcycle, 1200, "Sin novedad en el motor",
                List.of(new OrderDelivered.Job("OIL_CHANGE", "Cambio de aceite")), List.of(), 0, 0, List.of(),
                owner.userId(), null, null, Instant.parse("2026-06-01T15:00:00Z"));

        assertThatThrownBy(() -> history.execute(delivered)).isInstanceOf(IllegalStateException.class).hasMessageContaining("chain not started");
        assertThat(count(motorcycle, "SERVICE")).isZero();

        history.execute(new MotorcycleRegistered(motorcycle, 1000, workshop, Instant.parse("2026-06-01T14:00:00Z")));
        history.execute(delivered);
        assertThat(count(motorcycle, "INITIAL_REGISTRATION")).isEqualTo(1);
        assertThat(count(motorcycle, "SERVICE")).isEqualTo(1);
    }

    private long count(UUID motorcycle, String type) {
        return jdbc.sql("SELECT count(*) FROM history.history_entry WHERE motorcycle_id = ? AND type = ?")
                .params(motorcycle, type).query(Long.class).single();
    }
}
