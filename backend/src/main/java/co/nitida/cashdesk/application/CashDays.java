package co.nitida.cashdesk.application;

import co.nitida.cashdesk.domain.BogotaDay;
import co.nitida.shared.ApiException;
import java.time.Clock;
import java.time.LocalDate;

/** A cash count day can be today or a past Bogota day, never a future one. */
final class CashDays {
    private CashDays() {}

    static LocalDate requireNotFuture(LocalDate day, Clock clock) {
        if (day.isAfter(BogotaDay.of(clock.instant()))) {
            throw ApiException.unprocessable("FUTURE_DAY", "No se puede cuadrar un día que todavía no llega");
        }
        return day;
    }
}
