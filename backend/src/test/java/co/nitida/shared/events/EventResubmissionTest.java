package co.nitida.shared.events;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.events.IncompleteEventPublications;

class EventResubmissionTest {
    @Test
    void publicationsThatNeverCompletedAreResubmittedAfterAGracePeriod() {
        var incomplete = mock(IncompleteEventPublications.class);
        new EventResubmission(incomplete).resubmit();
        // not "now": a publication still being processed must not be delivered twice
        verify(incomplete).resubmitIncompletePublicationsOlderThan(Duration.ofMinutes(2));
    }
}
