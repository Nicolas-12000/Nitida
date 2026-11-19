package co.nitida.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Kills PIT mutants in {@link Texts} PII scans (linear email/digit detection). */
class TextsPiiScanTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "juan.perez@gmail.com",
            "a@b.co",
            "user+tag@mail-host.co",
            "foo%bar@x.co",
            "a_b@x.co",
            "x@y.co",
    })
    void detectsWorkshopEmails(String emailToken) {
        assertThat(Texts.containsEmailLike("Nota: " + emailToken + " fin")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "sin arroba ni nada raro",
            "solo x@y.c no alcanza",
            "@sin-local.com",
            "arroba suelta al final @",
    })
    void ignoresAlmostEmails(String text) {
        assertThat(Texts.containsEmailLike(text)).isFalse();
    }

    @Test
    void digitRunBoundaries() {
        assertThat(Texts.containsLongDigitRun("ref 1234567 ok")).isFalse();
        assertThat(Texts.containsLongDigitRun("ref 12345678 ok")).isTrue();
        assertThat(Texts.containsLongDigitRun("1234567- fin")).isFalse();
        assertThat(Texts.containsLongDigitRun("12345678- fin")).isTrue();
    }

    @Test
    void longDigitRunWithThirtyDigitsStillCountsAsPii() {
        String thirty = "1".repeat(30);
        assertThat(Texts.containsLongDigitRun("id " + thirty + " x")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Ocho dígitos 12345678 en la nota",
            "Correo corto a@b.co en taller",
            "Más +user@mail-host.co aquí",
    })
    void requirePublicRejectsScanHits(String text) {
        assertThatThrownBy(() -> Texts.requirePublic(text, 10, 2000, "CODE", "msg"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo("PERSONAL_DATA");
    }
}