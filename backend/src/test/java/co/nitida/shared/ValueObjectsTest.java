package co.nitida.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ValueObjectsTest {

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 23450, 999_999})
    void mileageAcceptsBoundaryValues(int km) {
        assertThat(new Mileage(km).km()).isEqualTo(km);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1_000_000})
    void mileageRejectsOutOfRange(int km) {
        assertThatThrownBy(() -> new Mileage(km)).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code()).isEqualTo("INVALID_MILEAGE");
    }

    @Test
    void mileageEqualToLastIsValidAndLowerIsRegression() {
        var last = new Mileage(1000);
        new Mileage(1000).validateAgainst(last);
        new Mileage(1001).validateAgainst(last);
        assertThatThrownBy(() -> new Mileage(999).validateAgainst(last))
                .extracting(e -> ((ApiException) e).code()).isEqualTo("MILEAGE_REGRESSION");
    }

    @Test
    void firstReadingHasNothingToCompareWith() {
        new Mileage(0).validateAgainst(null);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 50_000_000})
    void lineAmountAcceptsBoundaries(long v) {
        assertThat(CopAmount.line(v).value()).isEqualTo(v);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 50_000_001})
    void lineAmountRejectsOutOfRange(long v) {
        assertThatThrownBy(() -> CopAmount.line(v)).isInstanceOf(ApiException.class);
    }

    @Test
    void amountArithmeticNeverOverflowsSilently() {
        assertThat(CopAmount.line(1_000).times(999).plus(CopAmount.line(500)).value()).isEqualTo(999_500);
        assertThatThrownBy(() -> new CopAmount(Long.MAX_VALUE).plus(new CopAmount(1))).isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> new CopAmount(Long.MAX_VALUE).times(2)).isInstanceOf(ArithmeticException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"3101234567", "310 123 4567", "+573101234567", "573101234567", "6027201234"})
    void phonesNormalizeToE164(String raw) {
        assertThat(PhoneNumbers.normalize(raw)).startsWith("+57").hasSize(13);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "12345", "2101234567", "+1555123456", "abc"})
    void invalidPhonesAreRejected(String raw) {
        assertThatThrownBy(() -> PhoneNumbers.normalize(raw)).isInstanceOf(ApiException.class);
    }

    @Test
    void uuidV7IsTimeOrderedAndVersion7() throws InterruptedException {
        var a = UuidV7.next();
        Thread.sleep(2);
        var b = UuidV7.next();
        assertThat(a.version()).isEqualTo(7);
        assertThat(a.toString().compareTo(b.toString())).isNegative();
    }

    @Test
    void workshopContextRequiresAnyOfTheAllowedRoles() {
        var ctx = new WorkshopContext(UuidV7.next(), UuidV7.next(), java.util.Set.of(Role.MECHANIC));
        ctx.require(Role.MECHANIC, Role.WORKSHOP_OWNER);
        assertThatThrownBy(() -> ctx.require(Role.WORKSHOP_OWNER, Role.FRONT_DESK)).isInstanceOf(ApiException.class);
    }
}
