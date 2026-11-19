package co.nitida.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.nitida.shared.ApiException;
import co.nitida.shared.Mileage;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

/** P6 and D4 for the mileage rule (RN-45), with a plain reference model written separately. */
class MileageRulesTest {

    /** P6: every non-decreasing sequence is accepted. */
    @Property
    void nonDecreasingSequencesAreAccepted(@ForAll List<@IntRange(min = 0, max = 999_999) Integer> readings) {
        var sorted = new ArrayList<>(readings);
        sorted.sort(Integer::compare);
        Mileage last = null;
        for (int km : sorted) {
            var m = new Mileage(km);
            m.validateAgainst(last);
            last = m;
        }
    }

    /** P6: any regression is rejected, unless an odometer change reset the baseline first. */
    @Property
    void regressionsAreRejectedUnlessTheBaselineWasReset(@ForAll List<@IntRange(min = 0, max = 999_999) Integer> readings,
                                                        @ForAll List<Boolean> resets) {
        Integer model = null; // reference: just an integer and an if
        Mileage last = null;
        for (int i = 0; i < readings.size(); i++) {
            int km = readings.get(i);
            boolean reset = i < resets.size() && resets.get(i);
            if (reset) { // ODOMETER_CHANGE: the new reading becomes the baseline
                model = km;
                last = new Mileage(km);
                continue;
            }
            boolean acceptable = model == null || km >= model;
            if (acceptable) {
                new Mileage(km).validateAgainst(last);
                model = km;
                last = new Mileage(km);
            } else {
                int rejected = km;
                Mileage baseline = last;
                assertThatThrownBy(() -> new Mileage(rejected).validateAgainst(baseline)).isInstanceOf(ApiException.class);
            }
        }
        assertThat(true).isTrue();
    }

    @Test
    void errorCodeIsMileageRegression() {
        assertThatThrownBy(() -> new Mileage(99).validateAgainst(new Mileage(100)))
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("MILEAGE_REGRESSION"));
    }
}
