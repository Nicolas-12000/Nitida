package co.nitida.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.nitida.catalog.domain.ActiveSerials;
import co.nitida.catalog.domain.ProductCode;
import co.nitida.catalog.domain.Serial;
import co.nitida.catalog.domain.SerialNumber;
import co.nitida.shared.ApiException;
import co.nitida.shared.UuidV7;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CatalogDomainTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    /** Independent reference (left to right, weights depend on length parity) to cross-check the real one (D5). */
    static int referenceCheckDigit(String body) {
        int sum = 0;
        int n = body.length();
        for (int i = 0; i < n; i++) {
            int d = body.charAt(i) - '0';
            boolean weightThree = ((n - i) % 2 == 1); // rightmost body digit has weight 3
            sum += weightThree ? 3 * d : d;
        }
        return (10 - (sum % 10)) % 10;
    }

    @Provide
    Arbitrary<String> bodies() {
        return Arbitraries.of(7, 11, 12, 13).flatMap(len -> Arbitraries.strings().numeric().ofLength(len));
    }

    /** P11: appending the check digit gives a valid GTIN; changing any single digit makes it invalid. */
    @Property
    void validCodesAcceptedAndAnySingleDigitChangeRejected(@ForAll("bodies") String body, @ForAll int position, @ForAll int delta) {
        String code = body + referenceCheckDigit(body);
        assertThat(ProductCode.of(code).value()).isEqualTo(code);
        assertThat(ProductCode.checkDigit(body)).isEqualTo(referenceCheckDigit(body));
        int pos = Math.floorMod(position, code.length());
        int change = 1 + Math.floorMod(delta, 9);
        char[] chars = code.toCharArray();
        chars[pos] = (char) ('0' + (chars[pos] - '0' + change) % 10);
        assertThatThrownBy(() -> ProductCode.of(new String(chars))).isInstanceOf(ApiException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"4006381333931", "036000291452", "96385074", "10012345678902"})
    void realWorldCodesAreAccepted(String code) {
        assertThat(ProductCode.of(code).value()).isEqualTo(code);
    }

    @Test
    void typesFollowTheLength() {
        assertThat(ProductCode.of("96385074").type()).isEqualTo("EAN_8");
        assertThat(ProductCode.of("036000291452").type()).isEqualTo("UPC_A");
        assertThat(ProductCode.of("4006381333931").type()).isEqualTo("EAN_13");
        assertThat(ProductCode.of("10012345678902").type()).isEqualTo("GTIN_14");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1234567", "123456789", "12345678901", "123456789012345", "4006381333932", "40063813339A1", "4006 381333931", "-4006381333931"})
    void badLengthsDigitsAndCheckDigitsAreRejected(String code) {
        assertThatThrownBy(() -> ProductCode.of(code)).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code()).isEqualTo("INVALID_PRODUCT_CODE");
    }

    @Test
    void nullCodeIsRejected() {
        assertThatThrownBy(() -> ProductCode.of(null)).isInstanceOf(ApiException.class);
    }

    /** P12 (part): serial normalization is idempotent. */
    @Property
    void serialNormalizationIsIdempotent(@ForAll("serials") String raw) {
        var once = SerialNumber.of(raw);
        assertThat(SerialNumber.of(once.value())).isEqualTo(once);
        assertThat(once.hash()).hasSize(64).isEqualTo(SerialNumber.of(" " + raw.toLowerCase() + " ").hash());
    }

    @Provide
    Arbitrary<String> serials() {
        return Arbitraries.strings().withChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-/.").ofMinLength(4).ofMaxLength(64);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "", "a b", "ABC#1234", "ABC_1234", "ÁBCD1234"})
    void invalidSerialsAreRejected(String raw) {
        assertThatThrownBy(() -> SerialNumber.of(raw)).isInstanceOf(ApiException.class);
    }

    @Test
    void serialLengthBoundaries() {
        assertThatThrownBy(() -> SerialNumber.of("A".repeat(3))).isInstanceOf(ApiException.class);
        assertThat(SerialNumber.of("A".repeat(4)).value()).hasSize(4);
        assertThat(SerialNumber.of("A".repeat(64)).value()).hasSize(64);
        assertThatThrownBy(() -> SerialNumber.of("A".repeat(65))).isInstanceOf(ApiException.class);
        assertThat(SerialNumber.of("ab 12-3/4.").value()).isEqualTo("AB12-3/4.");
    }

    @Test
    void retireAndReactivateNeedAReasonAndFreeTheUniqueSlot() {
        var moto = UuidV7.next();
        var s = Serial.activate(UuidV7.next(), SerialNumber.of("BAT-12345"), moto, UuidV7.next(), UuidV7.next(), NOW);
        assertThat(s.isActive()).isTrue();
        assertThatThrownBy(() -> s.retire("no", NOW)).isInstanceOf(ApiException.class);
        s.retire("Batería reemplazada", NOW);
        assertThat(s.isActive()).isFalse();
        assertThatThrownBy(() -> s.retire("Batería reemplazada", NOW)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> s.reactivate(moto, UuidV7.next(), UuidV7.next(), " ", NOW)).isInstanceOf(ApiException.class);
        s.reactivate(moto, UuidV7.next(), UuidV7.next(), "Pieza reparada y reinstalada", NOW);
        assertThat(s.isActive()).isTrue();
    }

    record Move(int serial, int motorcycle, boolean retire) {}

    /** P10: any sequence of registers and retires leaves each serial active on at most one motorcycle. */
    @Property(tries = 200)
    void aSerialIsNeverActiveOnTwoMotorcycles(@ForAll("moves") List<Move> moves) {
        String[] serials = {"SER-0001", "SER-0002", "SER-0003", "SER-0004", "SER-0005"};
        UUID[] motos = {UuidV7.next(), UuidV7.next(), UuidV7.next()};
        var holder = new HashMap<String, UUID>();
        for (var move : moves) {
            String serial = serials[move.serial()];
            UUID moto = motos[move.motorcycle()];
            if (move.retire()) {
                holder.remove(serial);
                continue;
            }
            UUID current = holder.get(serial);
            if (ActiveSerials.claim(current, moto)) {
                UUID previous = holder.put(serial, moto);
                assertThat(previous == null || previous.equals(moto)).isTrue();
            } else {
                assertThat(holder).containsKey(serial);
                assertThat(holder.get(serial)).isNotEqualTo(moto);
            }
        }
    }

    @Provide
    Arbitrary<List<Move>> moves() {
        Arbitrary<Move> move = Combinators.combine(
                Arbitraries.integers().between(0, 4),
                Arbitraries.integers().between(0, 2),
                Arbitraries.of(true, false)).as(Move::new);
        return move.list().ofMinSize(1).ofMaxSize(40);
    }

    @Test
    void evidenceLevelIsTheHighestThePresentEvidenceAllows() {
        assertThat(EvidenceLevel.compute(false, false, false)).isEqualTo(EvidenceLevel.MANUAL);
        assertThat(EvidenceLevel.compute(true, false, false)).isEqualTo(EvidenceLevel.SCANNED);
        assertThat(EvidenceLevel.compute(true, true, false)).isEqualTo(EvidenceLevel.UNIQUE_SERIAL);
        assertThat(EvidenceLevel.compute(false, true, false)).isEqualTo(EvidenceLevel.UNIQUE_SERIAL);
        assertThat(EvidenceLevel.compute(true, true, true)).isEqualTo(EvidenceLevel.AUTHENTICITY_VERIFIED);
        assertThat(EvidenceLevel.compute(false, false, true)).isEqualTo(EvidenceLevel.AUTHENTICITY_VERIFIED);
    }
}
