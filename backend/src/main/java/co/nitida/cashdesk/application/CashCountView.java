package co.nitida.cashdesk.application;

import co.nitida.cashdesk.domain.CashCount;
import co.nitida.cashdesk.domain.CashCountStatus;
import java.time.Instant;
import java.time.LocalDate;

public record CashCountView(LocalDate day, long expected, Long counted, Long difference, CashCountStatus status,
                            Instant closedAt, String reopenReason) {
    static CashCountView of(CashCount c) {
        return new CashCountView(c.getCountDay(), c.getExpected(), c.getCounted(), c.getDifference(), c.getStatus(),
                c.getClosedAt(), c.getReopenReason());
    }

    /** A day not closed yet (or reopened): the expected cash is always computed live. */
    static CashCountView open(LocalDate day, long expected, String reopenReason) {
        return new CashCountView(day, expected, null, null, CashCountStatus.OPEN, null, reopenReason);
    }
}
