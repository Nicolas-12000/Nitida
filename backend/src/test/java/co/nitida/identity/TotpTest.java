package co.nitida.identity;

import static org.assertj.core.api.Assertions.assertThat;

import co.nitida.identity.domain.Base32;
import co.nitida.identity.domain.Totp;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** RFC 6238 (TOTP, SHA-1, 30 s, 6 digits) against its official test vectors, plus the window and replay rules. */
class TotpTest {
    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    /** RFC 6238 appendix B lists 8 digits; the 6-digit code is the last six. */
    @ParameterizedTest
    @CsvSource({"59,287082", "1111111109,081804", "1111111111,050471", "1234567890,005924", "2000000000,279037", "20000000000,353130"})
    void officialTestVectors(long epochSeconds, String expected) {
        assertThat(Totp.code(RFC_SECRET, Totp.stepAt(epochSeconds))).isEqualTo(expected);
    }

    @Test
    void acceptsTheCurrentStepAndOneStepOfClockDriftEitherWay() {
        long now = 1_111_111_109L;
        long step = Totp.stepAt(now);
        for (long s = step - 1; s <= step + 1; s++) {
            assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, s), now, 0)).hasValue(s);
        }
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step - 2), now, 0)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step + 2), now, 0)).isEmpty();
    }

    @Test
    void aCodeThatWasAlreadyUsedCannotBeUsedAgain() {
        long now = 1_111_111_109L;
        long step = Totp.stepAt(now);
        String code = Totp.code(RFC_SECRET, step);
        assertThat(Totp.verify(RFC_SECRET, code, now, step - 1)).hasValue(step);
        assertThat(Totp.verify(RFC_SECRET, code, now, step)).as("same step as the last accepted one").isEmpty();
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step - 1), now, step)).as("an older step").isEmpty();
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step + 1), now, step)).as("the next step is fine").hasValue(step + 1);
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL", "''", "12345", "1234567", "abcdef", "12 456", "'287082 '"}, nullValues = "NULL")
    void malformedCodesAreSimplyRejected(String code) {
        assertThat(Totp.verify(RFC_SECRET, code, 59, 0)).isEmpty();
    }

    @Test
    void generatedSecretsAreRandomAndTwentyBytes() {
        assertThat(Totp.newSecret()).hasSize(20).isNotEqualTo(Totp.newSecret());
    }

    @ParameterizedTest
    @CsvSource({"foobar,MZXW6YTBOI", "f,MY", "fo,MZXQ", "foo,MZXW6", "foob,MZXW6YQ", "fooba,MZXW6YTB"})
    void base32MatchesRfc4648(String plain, String encoded) {
        byte[] bytes = plain.getBytes(StandardCharsets.US_ASCII);
        assertThat(Base32.encode(bytes)).isEqualTo(encoded);
        assertThat(Base32.decode(encoded)).isEqualTo(bytes);
        assertThat(Base32.decode(encoded.toLowerCase() + "====")).as("authenticator apps show it lowercase, spaced and padded").isEqualTo(bytes);
    }

    @Test
    void base32RoundTripsEverySecretLength() {
        for (int n = 1; n <= 40; n++) {
            byte[] raw = co.nitida.shared.crypto.SecureTokens.randomBytes(n);
            assertThat(Base32.decode(Base32.encode(raw))).isEqualTo(raw);
        }
    }
}
