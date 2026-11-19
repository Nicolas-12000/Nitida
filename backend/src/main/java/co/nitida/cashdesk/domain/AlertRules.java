package co.nitida.cashdesk.domain;

import java.time.Duration;
import java.time.Instant;

/** Deterministic thresholds of the leak alerts (RN-32, RN-50). The threshold itself already triggers the alert. */
public final class AlertRules {
    private AlertRules() {}

    public static boolean intakeOverdue(Instant createdAt, Instant now, int thresholdHours) {
        return !Duration.between(createdAt, now).minus(Duration.ofHours(thresholdHours)).isNegative();
    }

    public static boolean orderStalled(Instant lastChangedAt, Instant now, int thresholdDays) {
        return !Duration.between(lastChangedAt, now).minus(Duration.ofDays(thresholdDays)).isNegative();
    }
}
