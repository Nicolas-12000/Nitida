package co.nitida.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.nitida.orders.domain.Job;
import co.nitida.orders.domain.JobType;
import co.nitida.orders.domain.WorkOrder;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * RN-22: the history is public, signed and immutable, so personal data typed into a free-text field would stay there
 * for good. Emails and long digit runs (phones, national ids) are refused where the text becomes public.
 */
class PublicTextTest {
    private static String code(Throwable t) { return ((ApiException) t).code(); }

    @ParameterizedTest
    @ValueSource(strings = {
            "El cliente Juan, correo juan.perez@gmail.com, trae la moto",
            "Llamar al 3101234567 cuando esté lista",
            "Llamar al 310 123 4567 cuando esté lista",
            "Llamar al 310-123-4567 cuando esté lista",
            "Cédula 1085123456 del propietario",
            "Cédula 1.085.123.456 del propietario",
            "teléfono +57 310 123 45 67"})
    void emailsPhonesAndNationalIdsAreRefused(String text) {
        assertThatThrownBy(() -> Texts.requirePublic(text, 10, 2000, "INVALID_DIAGNOSIS", "msg"))
                .satisfies(e -> assertThat(code(e)).isEqualTo("PERSONAL_DATA"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Ruido en la transmisión al acelerar, 23450 km en el tablero",
            "Cambio de kit de arrastre 428H x 118 pasos, piñón 14T",
            "Repuesto por $1.200.000 pesos, garantía 90 días",
            "Moto ABC12D con VIN 9C2JC4010NR000001 en revisión",
            "Presión de llantas 32 psi adelante y 36 psi atrás"})
    void ordinaryWorkshopTextWithNumbersPasses(String text) {
        assertThat(Texts.requirePublic(text, 10, 2000, "INVALID_DIAGNOSIS", "msg")).isEqualTo(text.strip());
    }

    @Test
    void theLengthRulesStillApplyFirst() {
        assertThatThrownBy(() -> Texts.requirePublic("corto", 10, 2000, "INVALID_DIAGNOSIS", "msg")).satisfies(e -> assertThat(code(e)).isEqualTo("INVALID_DIAGNOSIS"));
    }

    @Test
    void everyPublicFieldOfAnOrderIsGuarded() {
        var now = Instant.parse("2026-01-01T00:00:00Z");
        assertThatThrownBy(() -> WorkOrder.open(co.nitida.shared.UuidV7.next(), co.nitida.shared.UuidV7.next(), co.nitida.shared.UuidV7.next(),
                co.nitida.shared.UuidV7.next(), null, "Dueño Pedro, cel 3201112233, ruido raro", 0, null, now))
                .satisfies(e -> assertThat(code(e)).isEqualTo("PERSONAL_DATA"));
        var order = WorkOrder.open(co.nitida.shared.UuidV7.next(), co.nitida.shared.UuidV7.next(), co.nitida.shared.UuidV7.next(),
                co.nitida.shared.UuidV7.next(), null, "Ruido en la transmisión al acelerar", 0, null, now);
        assertThatThrownBy(() -> order.edit(now, "Escribir a pedro@correo.com sobre el ruido", null, null, null, null))
                .satisfies(e -> assertThat(code(e)).isEqualTo("PERSONAL_DATA"));
        assertThatThrownBy(() -> new Job(co.nitida.shared.UuidV7.next(), JobType.OTHER, "Avisar al 3201112233 cuando termine"))
                .satisfies(e -> assertThat(code(e)).isEqualTo("PERSONAL_DATA"));
    }
}
