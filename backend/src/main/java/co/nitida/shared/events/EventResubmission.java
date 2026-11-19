package co.nitida.shared.events;

import java.time.Duration;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Listeners run after commit in their own transaction, and a failure leaves the publication incomplete in the
 * registry. Nothing retries those by itself, so a database blip after a delivery would lose the history entry for good.
 * Every listener is idempotent (RN-64), which is what makes this retry safe.
 */
@Component
class EventResubmission {
    /** Not "now": a publication that is still being processed must not be delivered twice at once. */
    private static final Duration MIN_AGE = Duration.ofMinutes(2);

    private final IncompleteEventPublications incomplete;

    EventResubmission(IncompleteEventPublications incomplete) {
        this.incomplete = incomplete;
    }

    @Scheduled(fixedDelayString = "${nitida.events.resubmit-interval:PT5M}", initialDelayString = "${nitida.events.resubmit-interval:PT5M}")
    void resubmit() {
        incomplete.resubmitIncompletePublicationsOlderThan(MIN_AGE);
    }
}
