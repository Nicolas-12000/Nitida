package co.nitida.motorcycles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.nitida.motorcycles.domain.VehicleIdentifiers;
import co.nitida.shared.ApiException;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VehicleIdentifiersTest {

    /** P1: normalization is idempotent for any text. */
    @Property
    void normalizeIsIdempotent(@ForAll String raw) {
        String once = VehicleIdentifiers.normalize(raw);
        assertThat(VehicleIdentifiers.normalize(once)).isEqualTo(once);
    }

    @Property
    void normalizedValuesNeverContainSeparatorsOrLowercase(@ForAll String raw) {
        String n = VehicleIdentifiers.normalize(raw);
        if (n != null) assertThat(n).doesNotContain(" ", "-", ".").isEqualTo(n.toUpperCase());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABC12D", "abc12d", "abc-12-d", "ABC 12D"})
    void currentFormatPlatesAreAccepted(String raw) {
        var p = VehicleIdentifiers.plate(raw);
        assertThat(p.value()).isEqualTo("ABC12D");
        assertThat(p.legacy()).isFalse();
    }

    @Test
    void legacyFormatIsMarked() {
        var p = VehicleIdentifiers.plate("ABC12");
        assertThat(p.legacy()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"AB123D", "ABC1", "ABC12DE", "ABCD2D", "12345A", "ABC123", "", "A", "ÁBC12D"})
    void everyOtherPlateFormatIsRejected(String raw) {
        assertThatThrownBy(() -> VehicleIdentifiers.plate(raw)).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code()).isEqualTo("INVALID_PLATE");
    }

    @Test
    void vinMustBe17CharsWithoutIOQ() {
        assertThat(VehicleIdentifiers.vin("9c2jc4010nr000001")).isEqualTo("9C2JC4010NR000001");
        for (String bad : new String[] {"9C2JC4010NR00000", "9C2JC4010NR0000011", "9C2JC4010NR00000I", "9C2JC4010NR00000O",
                "9C2JC4010NR00000Q", ""}) {
            assertThatThrownBy(() -> VehicleIdentifiers.vin(bad)).isInstanceOf(ApiException.class);
        }
    }

    @Test
    void publicPlateIsMasked() {
        assertThat(VehicleIdentifiers.maskPlate("ABC12D")).isEqualTo("ABC**D");
        assertThat(VehicleIdentifiers.maskPlate("ABC12")).isEqualTo("ABC**");
        assertThat(VehicleIdentifiers.maskPlate(null)).isNull();
    }
}
