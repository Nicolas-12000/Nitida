package co.nitida.orders;

import static org.assertj.core.api.Assertions.assertThat;

import co.nitida.orders.domain.CashExpected;
import co.nitida.orders.domain.PaymentMethod;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** P9: expected cash equals the sum of non-voided cash, for any mix of methods and voids. */
class CashExpectedTest {

    @Property(tries = 200)
    void expectedIsTheSumOfNonVoidedCashWhateverTheMix(@ForAll("lines") List<CashExpected.Line> lines) {
        long model = 0;
        for (var line : lines) {
            if (line.method() == PaymentMethod.CASH && !line.voided()) model += line.amount();
        }
        assertThat(CashExpected.sum(lines)).isEqualTo(model);

        var cash = new ArrayList<CashExpected.Line>();
        var rest = new ArrayList<CashExpected.Line>();
        for (var line : lines) {
            (line.method() == PaymentMethod.CASH && !line.voided() ? cash : rest).add(line);
        }
        assertThat(CashExpected.sum(rest)).isZero();
        assertThat(CashExpected.sum(cash)).isEqualTo(model);
    }

    @Provide
    Arbitrary<List<CashExpected.Line>> lines() {
        Arbitrary<CashExpected.Line> line = Combinators.combine(
                Arbitraries.of(PaymentMethod.class),
                Arbitraries.longs().between(1, 50_000_000),
                Arbitraries.of(true, false)).as(CashExpected.Line::new);
        return line.list().ofMaxSize(40);
    }
}
