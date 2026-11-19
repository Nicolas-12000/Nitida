package co.nitida.workshops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.nitida.shared.ApiException;
import co.nitida.shared.UuidV7;
import co.nitida.workshops.domain.Workshop;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WorkshopDomainTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final UUID ADMIN = UuidV7.next();

    private static Workshop workshop() {
        return Workshop.register(UuidV7.next(), "Taller El Rayo", "900.123.456-7", "Calle 18 # 25-10", "Pasto", "310 123 4567", null, NOW);
    }

    private static String code(Throwable t) { return ((ApiException) t).code(); }

    @Test
    void bornPendingWithNormalizedData() {
        var w = workshop();
        assertThat(w.getStatus()).isEqualTo(WorkshopStatus.PENDING_VERIFICATION);
        assertThat(w.getDocumentNumber()).isEqualTo("900123456-7");
        assertThat(w.getPhone()).isEqualTo("+573101234567");
        assertThat(w.getIntakeAlertHours()).isEqualTo(24);
        assertThat(w.getCashTolerance()).isZero();
        assertThat(w.getStalledDays()).isEqualTo(7);
    }

    @Test
    void registrationFieldsAreValidated() {
        assertThatThrownBy(() -> Workshop.register(UuidV7.next(), "T", "900123456", "Calle 1 # 2-3", "Pasto", "3101234567", null, NOW))
                .satisfies(e -> assertThat(code(e)).isEqualTo("INVALID_NAME"));
        assertThatThrownBy(() -> Workshop.register(UuidV7.next(), "Taller", "12", "Calle 1 # 2-3", "Pasto", "3101234567", null, NOW))
                .satisfies(e -> assertThat(code(e)).isEqualTo("INVALID_DOCUMENT"));
        assertThatThrownBy(() -> Workshop.register(UuidV7.next(), "Taller", "900123456", "Cl", "Pasto", "3101234567", null, NOW))
                .satisfies(e -> assertThat(code(e)).isEqualTo("INVALID_ADDRESS"));
        assertThatThrownBy(() -> Workshop.register(UuidV7.next(), "Taller", "900123456", "Calle 1 # 2-3", "P", "3101234567", null, NOW))
                .satisfies(e -> assertThat(code(e)).isEqualTo("INVALID_MUNICIPALITY"));
    }

    @Test
    void statusMovesAlongAllowedEdgesOnly() {
        var w = workshop();
        w.changeStatus(WorkshopStatus.VERIFIED, ADMIN, NOW);
        assertThat(w.getVerifiedAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> w.changeStatus(WorkshopStatus.VERIFIED, ADMIN, NOW)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> w.changeStatus(WorkshopStatus.PENDING_VERIFICATION, ADMIN, NOW)).isInstanceOf(ApiException.class);
        w.changeStatus(WorkshopStatus.SUSPENDED, ADMIN, NOW.plusSeconds(5));
        assertThat(w.isSuspended()).isTrue();
        assertThat(w.getSuspendedAt()).isEqualTo(NOW.plusSeconds(5));
        w.changeStatus(WorkshopStatus.VERIFIED, ADMIN, NOW.plusSeconds(9));
        assertThat(w.isSuspended()).isFalse();
        assertThat(w.getSuspendedAt()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"1,0,1,true", "168,50000,90,true", "0,0,7,false", "169,0,7,false", "24,-1,7,false", "24,50001,7,false", "24,0,0,false", "24,0,91,false"})
    void settingsBoundaries(int hours, long tolerance, int days, boolean valid) {
        var w = workshop();
        if (valid) {
            w.updateSettings(hours, tolerance, days);
            assertThat(w.getIntakeAlertHours()).isEqualTo(hours);
            assertThat(w.getCashTolerance()).isEqualTo(tolerance);
            assertThat(w.getStalledDays()).isEqualTo(days);
        } else {
            assertThatThrownBy(() -> w.updateSettings(hours, tolerance, days)).satisfies(e -> assertThat(code(e)).isEqualTo("INVALID_SETTING"));
        }
    }
}
