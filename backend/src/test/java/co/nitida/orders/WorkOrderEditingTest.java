package co.nitida.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.nitida.catalog.EvidenceLevel;
import co.nitida.orders.domain.OrderPhoto;
import co.nitida.orders.domain.Part;
import co.nitida.orders.domain.PartCondition;
import co.nitida.orders.domain.PaymentMethod;
import co.nitida.orders.domain.WorkOrder;
import co.nitida.shared.ApiException;
import co.nitida.shared.UuidV7;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Editing rules of the work order aggregate: ranges, "activity" timestamps (F5) and what is closed once delivered. */
class WorkOrderEditingTest {
    static final Instant LATER = Instant.parse("2026-02-01T10:00:00Z");

    static WorkOrder order() { return WorkOrderDomainTest.order(); }

    @ParameterizedTest
    @CsvSource({"0,true", "3650,true", "-1,false", "3651,false"})
    void warrantyDaysBoundaries(int days, boolean valid) {
        var o = order();
        if (valid) {
            o.edit(LATER, null, null, days, null, null);
            assertThat(o.getWarrantyDays()).isEqualTo(days);
        } else {
            assertThatThrownBy(() -> o.edit(LATER, null, null, days, null, null)).satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("INVALID_WARRANTY"));
        }
    }

    @ParameterizedTest
    @CsvSource({"0,true", "100000,true", "-1,false", "100001,false"})
    void warrantyKmBoundaries(int km, boolean valid) {
        var o = order();
        if (valid) {
            o.edit(LATER, null, null, null, km, null);
            assertThat(o.getWarrantyKm()).isEqualTo(km);
        } else {
            assertThatThrownBy(() -> o.edit(LATER, null, null, null, km, null)).satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("INVALID_WARRANTY"));
        }
    }

    @Test
    void editReplacesTheJobsAndLeavesUnsentFieldsAlone() {
        var o = order();
        o.edit(LATER, null, 5_000L, null, null, List.of(WorkOrderDomainTest.job(), WorkOrderDomainTest.job()));
        assertThat(o.getJobs()).hasSize(2);
        o.edit(LATER, null, null, null, null, List.of(WorkOrderDomainTest.job()));
        assertThat(o.getJobs()).as("replaced, not appended").hasSize(1);
        assertThat(o.getLaborCost()).as("null leaves the labor cost unchanged").isEqualTo(5_000L);
        o.edit(LATER, null, null, null, null, null);
        assertThat(o.getJobs()).as("null leaves the jobs unchanged").hasSize(1);
    }

    @Test
    void everyChangeCountsAsActivityForTheStalledOrderAlert() {
        var o = order();
        assertThat(o.getUpdatedAt()).isEqualTo(WorkOrderDomainTest.NOW);

        o.edit(LATER, "Nuevo diagnóstico de la moto", null, null, null, null);
        assertThat(o.getUpdatedAt()).as("edit").isEqualTo(LATER);

        var later2 = LATER.plusSeconds(60);
        o.addPart(WorkOrderDomainTest.part(1, 1_000), later2);
        assertThat(o.getUpdatedAt()).as("addPart").isEqualTo(later2);

        var later3 = LATER.plusSeconds(120);
        o.addPhoto(new OrderPhoto(UuidV7.next(), UuidV7.next(), OrderPhoto.Moment.BEFORE), later3);
        assertThat(o.getUpdatedAt()).as("addPhoto").isEqualTo(later3);

        var later4 = LATER.plusSeconds(180);
        o.markChanged(later4);
        assertThat(o.getUpdatedAt()).as("markChanged").isEqualTo(later4);

        var later5 = LATER.plusSeconds(240);
        var part = o.getParts().get(0);
        assertThat(o.removePart(part.getId(), later5)).isSameAs(part);
        assertThat(o.getUpdatedAt()).as("removePart").isEqualTo(later5);

        var later6 = LATER.plusSeconds(300);
        o.edit(later6, null, null, null, null, List.of(WorkOrderDomainTest.job()));
        o.transition(OrderStatus.IN_PROGRESS, null, later6.plusSeconds(1));
        assertThat(o.getUpdatedAt()).as("transition").isEqualTo(later6.plusSeconds(1));
    }

    @Test
    void deliveringCountsAsActivityAndClosesEveryEditingDoor() {
        var o = order();
        o.edit(LATER, null, 10_000L, null, null, List.of(WorkOrderDomainTest.job()));
        o.transition(OrderStatus.IN_PROGRESS, null, LATER);
        o.transition(OrderStatus.COMPLETED, null, LATER);
        var pay = WorkOrderDomainTest.payment(o, PaymentMethod.CASH, 10_000);
        var deliveredAt = LATER.plusSeconds(500);
        o.deliver(List.of(pay), deliveredAt);
        assertThat(o.getUpdatedAt()).isEqualTo(deliveredAt);
        assertThat(o.getDeliveredAt()).isEqualTo(deliveredAt);

        UUID anyPart = UuidV7.next();
        for (Runnable attempt : List.<Runnable>of(
                () -> o.addPhoto(new OrderPhoto(UuidV7.next(), UuidV7.next(), OrderPhoto.Moment.AFTER), LATER),
                () -> o.markChanged(LATER),
                () -> o.removePart(anyPart, LATER),
                () -> o.addPart(WorkOrderDomainTest.part(1, 1), LATER))) {
            assertThatThrownBy(attempt::run).satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("ORDER_NOT_EDITABLE"));
        }
    }

    @Test
    void anUnknownPartIsNotFoundAndAKnownOneIsReturned() {
        var o = order();
        var p = WorkOrderDomainTest.part(1, 1_000);
        o.addPart(p, LATER);
        assertThat(o.findPart(p.getId())).isSameAs(p);
        assertThatThrownBy(() -> o.findPart(UuidV7.next())).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> o.removePart(UuidV7.next(), LATER)).isInstanceOf(ApiException.class);
    }

    @Test
    void cancellationReasonIsTrimmedAndRequired() {
        var o = order();
        o.edit(LATER, null, null, null, null, List.of(WorkOrderDomainTest.job()));
        assertThatThrownBy(() -> o.transition(OrderStatus.CANCELLED, "   ", LATER)).satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("REASON_REQUIRED"));
        o.transition(OrderStatus.CANCELLED, "  El cliente se arrepintió  ", LATER);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void evidenceLevelFollowsTheEvidencePresent() {
        var scanned = new Part(UuidV7.next(), "Filtro", null, "Marca", PartCondition.OEM, 1, 1_000, "7701234567892", "EAN13", null);
        assertThat(scanned.getEvidenceLevel()).as("a product code from the start").isEqualTo(EvidenceLevel.SCANNED);

        var manual = WorkOrderDomainTest.part(1, 1_000);
        assertThat(manual.getEvidenceLevel()).isEqualTo(EvidenceLevel.MANUAL);
        manual.attachSerial(UuidV7.next(), "ABCD1234");
        assertThat(manual.getEvidenceLevel()).isEqualTo(EvidenceLevel.UNIQUE_SERIAL);
        manual.attachAuthenticity(UuidV7.next(), "QR del sello leído");
        assertThat(manual.getEvidenceLevel()).isEqualTo(EvidenceLevel.AUTHENTICITY_VERIFIED);

        var seal = WorkOrderDomainTest.part(1, 1_000);
        assertThatThrownBy(() -> seal.attachAuthenticity(null, "QR del sello leído")).satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("PHOTO_REQUIRED"));
        assertThatThrownBy(() -> seal.attachAuthenticity(UuidV7.next(), "1234")).satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("INVALID_METHOD"));
        seal.attachAuthenticity(UuidV7.next(), "12345"); // 5 characters is the minimum
        assertThat(seal.getEvidenceLevel()).isEqualTo(EvidenceLevel.AUTHENTICITY_VERIFIED);
    }
}
