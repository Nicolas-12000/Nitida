package co.nitida.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.nitida.catalog.domain.Serial;
import co.nitida.shared.ApiException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Reasons that explain a void (payment) or a serial retirement/reactivation: 5 to 500 / 5 to 200 characters, trimmed. */
class ReasonLengthTest {
    static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private static String code(Throwable t) { return ((ApiException) t).code(); }

    @Test
    void paymentVoidReasonBounds() {
        var o = WorkOrderDomainTest.order();
        var ok5 = WorkOrderDomainTest.payment(o, co.nitida.orders.domain.PaymentMethod.CASH, 1_000);
        ok5.voidIt("12345", NOW);
        assertThat(ok5.isVoided()).isTrue();
        WorkOrderDomainTest.payment(o, co.nitida.orders.domain.PaymentMethod.CASH, 1_000).voidIt("x".repeat(500), NOW);
        assertThatThrownBy(() -> WorkOrderDomainTest.payment(o, co.nitida.orders.domain.PaymentMethod.CASH, 1_000).voidIt("1234", NOW))
                .satisfies(e -> assertThat(code(e)).isEqualTo("INVALID_REASON"));
        assertThatThrownBy(() -> WorkOrderDomainTest.payment(o, co.nitida.orders.domain.PaymentMethod.CASH, 1_000).voidIt("x".repeat(501), NOW))
                .satisfies(e -> assertThat(code(e)).isEqualTo("INVALID_REASON"));
        assertThatThrownBy(() -> WorkOrderDomainTest.payment(o, co.nitida.orders.domain.PaymentMethod.CASH, 1_000).voidIt("    ", NOW))
                .satisfies(e -> assertThat(code(e)).isEqualTo("INVALID_REASON"));
    }

    @Test
    void serialReasonBoundsAndTrimming() {
        assertThat(Serial.requireReason("  pieza reparada  ", "CODE", "msg")).isEqualTo("pieza reparada");
        assertThat(Serial.requireReason("12345", "CODE", "msg")).hasSize(5);
        assertThat(Serial.requireReason("x".repeat(200), "CODE", "msg")).hasSize(200);
        assertThatThrownBy(() -> Serial.requireReason("1234", "MY_CODE", "msg")).satisfies(e -> assertThat(code(e)).isEqualTo("MY_CODE"));
        assertThatThrownBy(() -> Serial.requireReason("x".repeat(201), "MY_CODE", "msg")).satisfies(e -> assertThat(code(e)).isEqualTo("MY_CODE"));
        assertThatThrownBy(() -> Serial.requireReason(null, "MY_CODE", "msg")).satisfies(e -> assertThat(code(e)).isEqualTo("MY_CODE"));
    }
}
