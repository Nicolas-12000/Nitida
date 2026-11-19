package co.nitida.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.nitida.orders.domain.Job;
import co.nitida.orders.domain.JobType;
import co.nitida.orders.domain.Part;
import co.nitida.orders.domain.PartCondition;
import co.nitida.orders.domain.Payment;
import co.nitida.orders.domain.PaymentMethod;
import co.nitida.orders.domain.WorkOrder;
import co.nitida.shared.ApiException;
import co.nitida.shared.UuidV7;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class WorkOrderDomainTest {
    static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    static final UUID WS = UuidV7.next();

    static WorkOrder order() {
        return WorkOrder.open(UuidV7.next(), WS, UuidV7.next(), UuidV7.next(), UuidV7.next(), "Ruido en la transmisión al acelerar", 1000, UuidV7.next(), NOW);
    }

    static Job job() { return new Job(UuidV7.next(), JobType.OIL_CHANGE, "Cambio de aceite"); }

    static Part part(int qty, long price) {
        return new Part(UuidV7.next(), "Filtro", null, "Marca", PartCondition.OEM, qty, price, null, null, null);
    }

    static Payment payment(WorkOrder o, PaymentMethod m, long amount) {
        return new Payment(UuidV7.next(), WS, o.getId(), m, amount, UuidV7.next(), NOW);
    }

    static String code(Throwable t) { return ((ApiException) t).code(); }

    @ParameterizedTest
    @CsvSource({"9,false", "10,true", "2000,true", "2001,false"})
    void diagnosisLengthBoundaries(int length, boolean valid) {
        String d = "d".repeat(length);
        if (valid) assertThat(WorkOrder.open(UuidV7.next(), WS, UuidV7.next(), UuidV7.next(), null, d, 0, null, NOW).getDiagnosis()).hasSize(length);
        else assertThatThrownBy(() -> WorkOrder.open(UuidV7.next(), WS, UuidV7.next(), UuidV7.next(), null, d, 0, null, NOW))
                .satisfies(e -> assertThat(code(e)).isEqualTo("INVALID_DIAGNOSIS"));
    }

    @ParameterizedTest
    @CsvSource({"1,true", "999,true", "1000,false", "-1,false"})
    void partQuantityBoundaries(int qty, boolean valid) {
        if (valid) assertThat(part(qty, 100).getQuantity()).isEqualTo(qty);
        else assertThatThrownBy(() -> part(qty, 100)).isInstanceOf(ApiException.class);
    }

    @Test
    void partQuantityZeroIsRejected() {
        assertThatThrownBy(() -> part(0, 100)).satisfies(e -> assertThat(code(e)).isEqualTo("INVALID_QUANTITY"));
    }

    @ParameterizedTest
    @CsvSource({"0,true", "1,true", "50000000,true", "50000001,false", "-1,false"})
    void partPriceBoundaries(long price, boolean valid) {
        if (valid) assertThat(part(1, price).getUnitPrice()).isEqualTo(price);
        else assertThatThrownBy(() -> part(1, price)).satisfies(e -> assertThat(code(e)).isEqualTo("INVALID_AMOUNT"));
    }

    @Test
    void laborCostBoundaries() {
        var o = order();
        o.edit(NOW, null, 50_000_000L, null, null, null);
        assertThat(o.getLaborCost()).isEqualTo(50_000_000L);
        assertThatThrownBy(() -> o.edit(NOW, null, 50_000_001L, null, null, null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> o.edit(NOW, null, -1L, null, null, null)).isInstanceOf(ApiException.class);
    }

    @Test
    void totalIsLinesPlusLaborAndStartsAtZero() {
        var o = order();
        assertThat(o.total().value()).isZero();
        o.addPart(part(3, 10_000), NOW);
        o.addPart(part(1, 2_500), NOW);
        o.edit(NOW, null, 7_000L, null, null, null);
        assertThat(o.total().value()).isEqualTo(3 * 10_000 + 2_500 + 7_000);
    }

    /** P5: total = sum of lines + labor; never negative; bounded generators never overflow. */
    @Property
    void totalIsTheSumOfItsParts(@ForAll("lines") List<long[]> lines, @ForAll("labor") long labor) {
        var o = order();
        long expected = labor;
        for (long[] l : lines) {
            o.addPart(part((int) l[0], l[1]), NOW);
            expected += l[0] * l[1];
        }
        o.edit(NOW, null, labor, null, null, null);
        assertThat(o.total().value()).isEqualTo(expected).isGreaterThanOrEqualTo(0);
    }

    @Provide
    Arbitrary<List<long[]>> lines() {
        return Arbitraries.longs().between(1, 999).flatMap(q -> Arbitraries.longs().between(0, 50_000_000L).map(p -> new long[] {q, p})).list().ofMaxSize(15);
    }

    @Provide
    Arbitrary<Long> labor() {
        return Arbitraries.longs().between(0, 50_000_000L);
    }

    // ---- state machine (RN-40) ----

    @Test
    void happyPathThroughTheStateMachine() {
        var o = order();
        o.transition(OrderStatus.IN_PROGRESS, null, NOW);
        o.edit(NOW, null, null, null, null, List.of(job()));
        o.transition(OrderStatus.COMPLETED, null, NOW);
        o.transition(OrderStatus.IN_PROGRESS, "El cliente pidió un trabajo adicional", NOW); // reopen needs a reason
        o.transition(OrderStatus.COMPLETED, null, NOW);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void completingNeedsAtLeastOneJob() {
        var o = order();
        o.transition(OrderStatus.IN_PROGRESS, null, NOW);
        assertThatThrownBy(() -> o.transition(OrderStatus.COMPLETED, null, NOW)).satisfies(e -> assertThat(code(e)).isEqualTo("JOBS_REQUIRED"));
    }

    @Test
    void reopeningAndCancellingNeedAReason() {
        var o = order();
        o.edit(NOW, null, null, null, null, List.of(job()));
        o.transition(OrderStatus.IN_PROGRESS, null, NOW);
        o.transition(OrderStatus.COMPLETED, null, NOW);
        assertThatThrownBy(() -> o.transition(OrderStatus.IN_PROGRESS, " ", NOW)).satisfies(e -> assertThat(code(e)).isEqualTo("REASON_REQUIRED"));
        var c = order();
        assertThatThrownBy(() -> c.transition(OrderStatus.CANCELLED, null, NOW)).satisfies(e -> assertThat(code(e)).isEqualTo("REASON_REQUIRED"));
        c.transition(OrderStatus.CANCELLED, "El cliente retiró la moto sin reparar", NOW);
        assertThat(c.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"OPEN", "COMPLETED", "DELIVERED"})
    void deliveredIsNeverAPlainTransition(String target) {
        var o = order();
        assertThatThrownBy(() -> o.transition(OrderStatus.valueOf(target), "motivo suficiente", NOW))
                .satisfies(e -> assertThat(code(e)).isEqualTo("INVALID_TRANSITION"));
    }

    /** Written separately from OrderStatus on purpose (D4): the allowed edges of RN-40 as a plain map. */
    private static final Map<OrderStatus, Set<OrderStatus>> MODEL = Map.of(
            OrderStatus.OPEN, Set.of(OrderStatus.IN_PROGRESS, OrderStatus.CANCELLED),
            OrderStatus.IN_PROGRESS, Set.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED),
            OrderStatus.COMPLETED, Set.of(OrderStatus.IN_PROGRESS),   // DELIVERED only via deliver()
            OrderStatus.DELIVERED, Set.of(),
            OrderStatus.CANCELLED, Set.of());

    /** P7 + D4: any random sequence of transitions agrees with the reference model and only reaches RN-40 states. */
    @Property(tries = 400)
    void randomTransitionSequencesFollowTheReferenceModel(@ForAll("targets") List<OrderStatus> targets) {
        var o = order();
        o.edit(NOW, null, null, null, null, List.of(job())); // jobs present so COMPLETED is not blocked
        OrderStatus expected = OrderStatus.OPEN;
        for (OrderStatus t : targets) {
            boolean allowed = MODEL.get(expected).contains(t);
            if (allowed) {
                o.transition(t, "motivo suficiente", NOW);
                expected = t;
            } else {
                assertThatThrownBy(() -> o.transition(t, "motivo suficiente", NOW)).isInstanceOf(ApiException.class);
            }
            assertThat(o.getStatus()).isEqualTo(expected);
        }
    }

    @Provide
    Arbitrary<List<OrderStatus>> targets() {
        return Arbitraries.of(OrderStatus.values()).list().ofMaxSize(12);
    }

    // ---- delivery (RN-47) ----

    private static WorkOrder completedWith(long laborCost) {
        var o = order();
        o.edit(NOW, null, laborCost, null, null, List.of(job()));
        o.transition(OrderStatus.IN_PROGRESS, null, NOW);
        o.transition(OrderStatus.COMPLETED, null, NOW);
        return o;
    }

    @Test
    void deliveryNeedsPaymentsEqualToTheTotalExactly() {
        var o = completedWith(10_000);
        assertThatThrownBy(() -> o.deliver(List.of(), NOW)).satisfies(e -> assertThat(code(e)).isEqualTo("INCOMPLETE_PAYMENTS"));
        assertThatThrownBy(() -> o.deliver(List.of(payment(o, PaymentMethod.CASH, 9_999)), NOW)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> o.deliver(List.of(payment(o, PaymentMethod.CASH, 10_001)), NOW)).isInstanceOf(ApiException.class);
        o.deliver(List.of(payment(o, PaymentMethod.CASH, 4_000), payment(o, PaymentMethod.TRANSFER, 6_000)), NOW);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(o.getDeliveredAt()).isEqualTo(NOW);
    }

    @Test
    void voidedPaymentsDoNotCount() {
        var o = completedWith(10_000);
        var voided = payment(o, PaymentMethod.CASH, 10_000);
        voided.voidIt("Se registró por error", NOW);
        assertThatThrownBy(() -> o.deliver(List.of(voided), NOW)).isInstanceOf(ApiException.class);
        o.deliver(List.of(voided, payment(o, PaymentMethod.CARD, 10_000)), NOW);
    }

    @Test
    void zeroTotalOnlyForWarrantyOrdersPointingToAnEarlierOne() {
        var plain = completedWith(0);
        assertThatThrownBy(() -> plain.deliver(List.of(), NOW)).satisfies(e -> assertThat(code(e)).isEqualTo("INVALID_WARRANTY"));
        var warranty = completedWith(0);
        warranty.markAsWarranty(UuidV7.next());
        warranty.deliver(List.of(), NOW);
        assertThat(warranty.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void onlyCompletedOrdersAreDelivered() {
        var o = order();
        assertThatThrownBy(() -> o.deliver(List.of(), NOW)).satisfies(e -> assertThat(code(e)).isEqualTo("INVALID_TRANSITION"));
    }

    @Test
    void deliveredOrderIsImmutable() {
        var o = completedWith(10_000);
        o.deliver(List.of(payment(o, PaymentMethod.CASH, 10_000)), NOW);
        assertThatThrownBy(() -> o.edit(NOW, "Otro diagnóstico distinto", null, null, null, null)).satisfies(e -> assertThat(code(e)).isEqualTo("ORDER_NOT_EDITABLE"));
        assertThatThrownBy(() -> o.addPart(part(1, 1), NOW)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> o.transition(OrderStatus.IN_PROGRESS, "motivo suficiente", NOW)).isInstanceOf(ApiException.class);
    }

    @Test
    void paymentRules() {
        var o = completedWith(10_000);
        assertThatThrownBy(() -> o.checkCanReceivePayment(PaymentMethod.CASH, 10_001, 0)).satisfies(e -> assertThat(code(e)).isEqualTo("OVERPAYMENT"));
        o.checkCanReceivePayment(PaymentMethod.CASH, 10_000, 0);
        o.checkCanReceivePayment(PaymentMethod.CASH, 1, 9_999);
        assertThatThrownBy(() -> o.checkCanReceivePayment(PaymentMethod.CASH, 2, 9_999)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> payment(o, PaymentMethod.CASH, 0)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> payment(o, PaymentMethod.CASH, 50_000_001)).isInstanceOf(ApiException.class);
        var noCustomer = WorkOrder.open(UuidV7.next(), WS, UuidV7.next(), UuidV7.next(), null, "Diagnóstico suficiente", 1, null, NOW);
        noCustomer.edit(NOW, null, 100L, null, null, null);
        assertThatThrownBy(() -> noCustomer.checkCanReceivePayment(PaymentMethod.STORE_CREDIT, 100, 0))
                .satisfies(e -> assertThat(code(e)).isEqualTo("CUSTOMER_REQUIRED"));
    }

    @Test
    void paymentCanBeVoidedOnceWithAReason() {
        var o = order();
        var p = payment(o, PaymentMethod.CASH, 100);
        assertThatThrownBy(() -> p.voidIt("no", NOW)).isInstanceOf(ApiException.class);
        p.voidIt("Error de digitación", NOW);
        assertThat(p.isVoided()).isTrue();
        assertThatThrownBy(() -> p.voidIt("Error de digitación", NOW)).satisfies(e -> assertThat(code(e)).isEqualTo("PAYMENT_ALREADY_VOIDED"));
    }

    @Test
    void optimisticLockCheckAndCancellationInfo() {
        var o = order();
        o.checkVersion(0);
        assertThatThrownBy(() -> o.checkVersion(5)).satisfies(e -> assertThat(code(e)).isEqualTo("VERSION_CONFLICT"));
        assertThat(o.hasParts()).isFalse();
        o.addPart(part(1, 10), NOW);
        assertThat(o.hasParts()).isTrue();
    }

    @Test
    void snapshotCarriesWhatHistoryNeedsAndIsImmutable() {
        var o = completedWith(5_000);
        o.addPart(part(2, 1_000), NOW);
        o.deliver(List.of(payment(o, PaymentMethod.CASH, 7_000)), NOW);
        var snap = o.snapshot(new ArrayList<>(List.of("hash1")));
        assertThat(snap.total()).isEqualTo(7_000);
        assertThat(snap.parts()).hasSize(1);
        assertThat(snap.jobs()).hasSize(1);
        assertThat(snap.photoHashes()).containsExactly("hash1");
        assertThatThrownBy(() -> snap.parts().add(null)).isInstanceOf(UnsupportedOperationException.class);
    }
}
