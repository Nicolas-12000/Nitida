package co.nitida.cashdesk.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** RN-52: "the day" of a cash count is the America/Bogota calendar day; everything else is stored in UTC. */
public final class BogotaDay {
    public static final ZoneId ZONE = ZoneId.of("America/Bogota");

    private BogotaDay() {}

    public static LocalDate of(Instant instant) {
        return instant.atZone(ZONE).toLocalDate();
    }

    /** Inclusive start of the day. */
    public static Instant start(LocalDate day) {
        return day.atStartOfDay(ZONE).toInstant();
    }

    /** Exclusive end: the start of the next day, so ranges are half-open [start, end). */
    public static Instant end(LocalDate day) {
        return start(day.plusDays(1));
    }
}
