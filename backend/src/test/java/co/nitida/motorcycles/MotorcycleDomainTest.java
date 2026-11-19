package co.nitida.motorcycles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.nitida.motorcycles.domain.Customer;
import co.nitida.motorcycles.domain.Motorcycle;
import co.nitida.shared.ApiException;
import co.nitida.shared.UuidV7;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MotorcycleDomainTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private static Motorcycle plateOnly() {
        return Motorcycle.create(UuidV7.next(), "ABC12D", null, "Yamaha", "FZ 2.0", 2022, 150, NOW);
    }

    @Test
    void needsAtLeastPlateOrVin() {
        assertThatThrownBy(() -> Motorcycle.create(UuidV7.next(), null, null, "Yamaha", "FZ", 2022, 150, NOW))
                .extracting(e -> ((ApiException) e).code()).isEqualTo("IDENTIFIER_REQUIRED");
    }

    @Test
    void declaredDataIsRangeChecked() {
        assertThatThrownBy(() -> Motorcycle.create(UuidV7.next(), "ABC12D", null, "Y", "FZ", 2022, 150, NOW)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Motorcycle.create(UuidV7.next(), "ABC12D", null, "Yamaha", "FZ", 1949, 150, NOW)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Motorcycle.create(UuidV7.next(), "ABC12D", null, "Yamaha", "FZ", 2022, 10, NOW)).isInstanceOf(ApiException.class);
        assertThat(Motorcycle.create(UuidV7.next(), "ABC12D", null, "Yamaha", "FZ", null, null, NOW).getModelYear()).isNull();
    }

    @Test
    void lateVinIsAssociatedButADifferentVinConflicts() {
        var m = plateOnly();
        m.associateVin("9C2JC4010NR000001");
        m.associateVin("9C2JC4010NR000001"); // same VIN again is fine
        assertThatThrownBy(() -> m.associateVin("9C2JC4010NR000002"))
                .extracting(e -> ((ApiException) e).code()).isEqualTo("PLATE_VIN_MISMATCH");
    }

    @Test
    void plateChangeKeepsIdentityAndUpdatesLegacyFlag() {
        var m = plateOnly();
        var id = m.getId();
        m.changePlate("xyz98");
        assertThat(m.getId()).isEqualTo(id);
        assertThat(m.getCurrentPlate()).isEqualTo("XYZ98");
        assertThat(m.isLegacyPlateFormat()).isTrue();
    }

    @Test
    void customerNeedsNameAndPhoneAndNormalizesOptionalFields() {
        var ws = UuidV7.next();
        var c = Customer.create(UuidV7.next(), ws, " Ana Gómez ", "310 123 4567", "1.085.123.456", "ANA@Mail.com", NOW);
        assertThat(c.getFullName()).isEqualTo("Ana Gómez");
        assertThat(c.getPhone()).isEqualTo("+573101234567");
        assertThat(c.getNationalId()).isEqualTo("1085123456");
        assertThat(c.getEmail()).isEqualTo("ana@mail.com");
        assertThatThrownBy(() -> Customer.create(UuidV7.next(), ws, "A", "3101234567", null, null, NOW)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Customer.create(UuidV7.next(), ws, "Ana", "123", null, null, NOW)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Customer.create(UuidV7.next(), ws, "Ana", "3101234567", "12ab", null, NOW)).isInstanceOf(ApiException.class);
    }

    @Test
    void erasedCustomerLosesPersonalDataAndCannotBeEdited() {
        var c = Customer.create(UuidV7.next(), UuidV7.next(), "Ana", "3101234567", "1085123456", "a@b.co", NOW);
        c.erase(NOW);
        assertThat(c.getFullName()).isEqualTo(Customer.ERASED_NAME);
        assertThat(c.getNationalId()).isNull();
        assertThat(c.getEmail()).isNull();
        assertThatThrownBy(() -> c.apply("Otra", "3101234567", null, null)).isInstanceOf(ApiException.class);
    }
}
