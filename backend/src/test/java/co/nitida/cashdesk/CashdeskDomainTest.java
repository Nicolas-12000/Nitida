package co.nitida.cashdesk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.nitida.cashdesk.domain.AlertRules;
import co.nitida.cashdesk.domain.AlertType;
import co.nitida.cashdesk.domain.BogotaDay;
import co.nitida.cashdesk.domain.CashCount;
import co.nitida.cashdesk.domain.CashCountStatus;
import co.nitida.cashdesk.domain.LeakAlert;
import co.nitida.shared.ApiException;
import co.nitida.shared.UuidV7;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CashdeskDomainTest {
    private static final Instant NOW = Instant.parse("2026-11-19T15:00:00Z");
    private static final UUID WS = UuidV7.next();
    private static final UUID OWNER = UuidV7.next();

    // ---- RN-52: the day belongs to America/Bogota (UTC-5, no DST) ----

    @Test
    void paymentsAtTheMidnightEdgeFallInDifferentBogotaDays() {
        Instant lastSecond = Instant.parse("2026-11-19T04:59:59Z"); // 23:59:59 of Nov 18 in Bogota
        Instant firstSecond = Instant.parse("2026-11-19T05:00:00Z"); // 00:00:00 of Nov 19 in Bogota
        assertThat(BogotaDay.of(lastSecond)).isEqualTo(LocalDate.parse("2026-11-18"));
        assertThat(BogotaDay.of(firstSecond)).isEqualTo(LocalDate.parse("2026-11-19"));
    }

    @Test
    void aDayRangeIsHalfOpenAndTwentyFourHoursLong() {
        var day = LocalDate.parse("2026-11-19");
        assertThat(BogotaDay.start(day)).isEqualTo(Instant.parse("2026-11-19T05:00:00Z"));
        assertThat(BogotaDay.end(day)).isEqualTo(Instant.parse("2026-11-20T05:00:00Z"));
        assertThat(Duration.between(BogotaDay.start(day), BogotaDay.end(day))).isEqualTo(Duration.ofHours(24));
    }

    // ---- close, tolerance boundaries, reopen ----

    @ParameterizedTest
    @CsvSource({"100000,100000,0,BALANCED", "100000,99999,0,UNBALANCED", "100000,100001,0,UNBALANCED",
            "100000,95000,5000,BALANCED", "100000,94999,5000,UNBALANCED", "100000,105000,5000,BALANCED", "100000,105001,5000,UNBALANCED",
            "0,0,0,BALANCED", "0,1,0,UNBALANCED"})
    void balancedWhenTheAbsoluteDifferenceIsWithinTolerance(long expected, long counted, long tolerance, CashCountStatus status) {
        var c = CashCount.close(UuidV7.next(), WS, LocalDate.parse("2026-11-18"), expected, counted, tolerance, OWNER, NOW);
        assertThat(c.getStatus()).isEqualTo(status);
        assertThat(c.getDifference()).isEqualTo(counted - expected);
        assertThat(c.getExpected()).isEqualTo(expected);
    }

    @Test
    void countedCashCannotBeNegative() {
        assertThatThrownBy(() -> CashCount.close(UuidV7.next(), WS, LocalDate.parse("2026-11-18"), 10, -1, 0, OWNER, NOW))
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("INVALID_AMOUNT"));
    }

    @Test
    void aClosedCountIsNotEditedAgainUntilReopenedWithAReason() {
        var c = CashCount.close(UuidV7.next(), WS, LocalDate.parse("2026-11-18"), 100, 100, 0, OWNER, NOW);
        assertThatThrownBy(() -> c.recount(90, 100, 0, OWNER, NOW)).satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("CASH_COUNT_CLOSED"));
        assertThatThrownBy(() -> c.reopen("corto", NOW)).satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("REASON_REQUIRED"));
        c.reopen("Falta registrar un pago en efectivo", NOW);
        assertThat(c.getStatus()).isEqualTo(CashCountStatus.OPEN);
        c.recount(150, 140, 20, OWNER, NOW);
        assertThat(c.getStatus()).isEqualTo(CashCountStatus.BALANCED);
        assertThatThrownBy(() -> c.reopen("Falta registrar un pago en efectivo", NOW).reopen("Otra vez sin cerrar", NOW)).isInstanceOf(ApiException.class);
    }

    // ---- RN-50: alert thresholds (exact boundaries from section 15.3) ----

    @ParameterizedTest
    @CsvSource({"23,59,false", "24,0,true", "24,1,true", "25,0,true"}) // hours,minutes open vs a 24 h threshold
    void intakeOverdueAtTheExactThresholdOrLater(long hours, long minutes, boolean overdue) {
        Instant created = NOW.minus(Duration.ofHours(hours)).minus(Duration.ofMinutes(minutes));
        // 23h59m is one minute under the threshold; 24h00m is exact
        Instant createdForCase = hours == 23 ? NOW.minus(Duration.ofHours(23)).minus(Duration.ofMinutes(59)) : created;
        assertThat(AlertRules.intakeOverdue(createdForCase, NOW, 24)).isEqualTo(overdue);
    }

    @Test
    void intakeThresholdBoundsFollowTheWorkshopSetting() {
        assertThat(AlertRules.intakeOverdue(NOW.minus(Duration.ofHours(1)), NOW, 1)).isTrue();
        assertThat(AlertRules.intakeOverdue(NOW.minus(Duration.ofMinutes(59)), NOW, 1)).isFalse();
        assertThat(AlertRules.intakeOverdue(NOW.minus(Duration.ofHours(168)), NOW, 168)).isTrue();
        assertThat(AlertRules.intakeOverdue(NOW.minus(Duration.ofHours(167)), NOW, 168)).isFalse();
    }

    @Test
    void orderStalledAfterTheConfiguredDays() {
        assertThat(AlertRules.orderStalled(NOW.minus(Duration.ofDays(7)), NOW, 7)).isTrue();
        assertThat(AlertRules.orderStalled(NOW.minus(Duration.ofDays(7)).plusSeconds(1), NOW, 7)).isFalse();
    }

    // ---- RN-53: alerts are never deleted, only attended ----

    @Test
    void alertIsAttendedOnceWithAComment() {
        var a = new LeakAlert(UuidV7.next(), WS, AlertType.F2_CANCELLED_WITH_PARTS, UuidV7.next(), "order:1", "{}", NOW);
        assertThat(a.isResolved()).isFalse();
        assertThatThrownBy(() -> a.resolve(OWNER, "no", NOW)).satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("COMMENT_REQUIRED"));
        a.resolve(OWNER, "Se habló con el mecánico, repuestos devueltos", NOW);
        assertThat(a.isResolved()).isTrue();
        assertThatThrownBy(() -> a.resolve(OWNER, "Intento de atender de nuevo", NOW)).satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("ALERT_ALREADY_RESOLVED"));
    }
}
