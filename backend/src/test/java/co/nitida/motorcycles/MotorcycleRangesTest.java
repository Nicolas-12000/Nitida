package co.nitida.motorcycles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.nitida.motorcycles.domain.Motorcycle;
import co.nitida.shared.ApiException;
import co.nitida.shared.UuidV7;
import java.time.Instant;
import java.time.Year;
import org.junit.jupiter.api.Test;

/** RN-16 sanity ranges of the declared data, at both edges. */
class MotorcycleRangesTest {
    static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private static Motorcycle make(String brand, String line, Integer year, Integer cc) {
        return Motorcycle.create(UuidV7.next(), "ABC12D", null, brand, line, year, cc, NOW);
    }

    private static String code(Throwable t) { return ((ApiException) t).code(); }

    @Test
    void modelYearFrom1950UntilNextYear() {
        int next = Year.now().getValue() + 1;
        assertThat(make("Yamaha", "FZ", 1950, null).getModelYear()).isEqualTo(1950);
        assertThat(make("Yamaha", "FZ", next, null).getModelYear()).isEqualTo(next);
        assertThatThrownBy(() -> make("Yamaha", "FZ", 1949, null)).satisfies(e -> assertThat(code(e)).isEqualTo("INVALID_MODEL_YEAR"));
        assertThatThrownBy(() -> make("Yamaha", "FZ", next + 1, null)).satisfies(e -> assertThat(code(e)).isEqualTo("INVALID_MODEL_YEAR"));
    }

    @Test
    void displacementFrom20To2500() {
        assertThat(make("Yamaha", "FZ", null, 20).getDisplacementCc()).isEqualTo(20);
        assertThat(make("Yamaha", "FZ", null, 2500).getDisplacementCc()).isEqualTo(2500);
        assertThatThrownBy(() -> make("Yamaha", "FZ", null, 19)).satisfies(e -> assertThat(code(e)).isEqualTo("INVALID_DISPLACEMENT"));
        assertThatThrownBy(() -> make("Yamaha", "FZ", null, 2501)).satisfies(e -> assertThat(code(e)).isEqualTo("INVALID_DISPLACEMENT"));
    }

    @Test
    void brandAndLineLengths() {
        assertThat(make("Ya", "F", null, null).getBrand()).isEqualTo("Ya");
        assertThat(make("a".repeat(40), "b".repeat(40), null, null).getLine()).hasSize(40);
        assertThatThrownBy(() -> make("Y", "FZ", null, null)).satisfies(e -> assertThat(code(e)).isEqualTo("INVALID_BRAND"));
        assertThatThrownBy(() -> make("a".repeat(41), "FZ", null, null)).satisfies(e -> assertThat(code(e)).isEqualTo("INVALID_BRAND"));
        assertThatThrownBy(() -> make("Yamaha", "", null, null)).satisfies(e -> assertThat(code(e)).isEqualTo("INVALID_LINE"));
        assertThatThrownBy(() -> make("Yamaha", "b".repeat(41), null, null)).satisfies(e -> assertThat(code(e)).isEqualTo("INVALID_LINE"));
    }
}
