package co.nitida.orders.domain;

import java.util.List;

/** RN-52 / P9: expected cash is the sum of non-voided cash. The day window is the query's job. */
public final class CashExpected {
    public record Line(PaymentMethod method, long amount, boolean voided) {}

    private CashExpected() {}

    public static long sum(List<Line> lines) {
        long total = 0;
        for (var line : lines) {
            if (line.method() == PaymentMethod.CASH && !line.voided()) total += line.amount();
        }
        return total;
    }
}
