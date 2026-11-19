package co.nitida.cashdesk.application;

import co.nitida.cashdesk.domain.AlertType;
import co.nitida.cashdesk.domain.LeakAlert;
import co.nitida.cashdesk.domain.LeakAlertRepository;
import co.nitida.shared.UuidV7;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Single entry point that raises alerts. The dedupe key makes every producer idempotent (scheduler runs, retried
 * listeners); a concurrent duplicate hits the unique constraint and the event registry retries.
 */
@Component
class LeakAlerts {
    private final LeakAlertRepository alerts;
    private final Clock clock;

    LeakAlerts(LeakAlertRepository alerts, Clock clock) {
        this.alerts = alerts;
        this.clock = clock;
    }

    record Candidate(UUID workshopId, AlertType type, UUID referenceId, String dedupeKey, String details) {}

    private static final int KEYS_PER_QUERY = 500;

    /** The scheduler's path: one query per 500 candidates instead of one per candidate, every 15 minutes, forever. */
    void raiseAll(List<Candidate> candidates) {
        var known = new HashSet<String>();
        var keys = candidates.stream().map(c -> c.type().name() + ":" + c.dedupeKey()).toList();
        for (int i = 0; i < keys.size(); i += KEYS_PER_QUERY) {
            known.addAll(alerts.findExistingKeys(keys.subList(i, Math.min(i + KEYS_PER_QUERY, keys.size()))));
        }
        var now = clock.instant();
        for (int i = 0; i < keys.size(); i++) {
            if (known.contains(keys.get(i))) continue;
            var c = candidates.get(i);
            alerts.save(new LeakAlert(UuidV7.next(), c.workshopId(), c.type(), c.referenceId(), keys.get(i), c.details(), now));
        }
    }

    /** Returns true if a new alert was created. Details never carry personal data nor data of other workshops. */
    boolean raise(UUID workshopId, AlertType type, UUID referenceId, String dedupeKey, String details) {
        String key = type.name() + ":" + dedupeKey;
        if (alerts.existsByWorkshopIdAndDedupeKey(workshopId, key)) return false;
        alerts.save(new LeakAlert(UuidV7.next(), workshopId, type, referenceId, key, details, clock.instant()));
        return true;
    }
}
