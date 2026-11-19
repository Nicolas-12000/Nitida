package co.nitida.cashdesk.application;

import co.nitida.cashdesk.domain.AlertType;
import co.nitida.cashdesk.domain.LeakAlert;
import co.nitida.cashdesk.domain.LeakAlertRepository;
import co.nitida.shared.UuidV7;
import java.time.Clock;
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

    /** Returns true if a new alert was created. Details never carry personal data nor data of other workshops. */
    boolean raise(UUID workshopId, AlertType type, UUID referenceId, String dedupeKey, String details) {
        String key = type.name() + ":" + dedupeKey;
        if (alerts.existsByWorkshopIdAndDedupeKey(workshopId, key)) return false;
        alerts.save(new LeakAlert(UuidV7.next(), workshopId, type, referenceId, key, details, clock.instant()));
        return true;
    }
}
