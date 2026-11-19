package co.nitida.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.nitida.identity.domain.AppUser;
import co.nitida.identity.domain.PasswordPolicy;
import co.nitida.shared.ApiException;
import co.nitida.shared.UuidV7;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AppUserAndPolicyTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private static AppUser user() {
        return new AppUser(UuidV7.next(), "a@b.co", "hash", "A", false, NOW);
    }

    @Test
    void locksProgressivelyFromTheFifthFailure() {
        var u = user();
        for (int i = 0; i < 4; i++) u.registerFailure(NOW);
        assertThat(u.isLocked(NOW)).isFalse();
        u.registerFailure(NOW); // 5th: 1 minute
        assertThat(u.isLocked(NOW.plusSeconds(59))).isTrue();
        assertThat(u.isLocked(NOW.plus(Duration.ofMinutes(1)))).isFalse();
        u.registerFailure(NOW); // 6th: 2 minutes
        assertThat(u.isLocked(NOW.plus(Duration.ofMinutes(1)))).isTrue();
    }

    @Test
    void lockNeverExceedsOneHourAndSuccessResets() {
        var u = user();
        for (int i = 0; i < 50; i++) u.registerFailure(NOW);
        assertThat(u.getLockedUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(60)));
        u.registerSuccess();
        assertThat(u.isLocked(NOW)).isFalse();
        assertThat(u.getFailedAttempts()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"corta", "12345678901", "password1234", "aaaaaaaaaaaaaaaa", "Contraseña123"})
    void weakPasswordsAreRejected(String p) {
        assertThatThrownBy(() -> PasswordPolicy.validate(p)).isInstanceOf(ApiException.class);
    }

    @Test
    void acceptsLongEnoughUncommonPassword() {
        PasswordPolicy.validate("una-clave-larga-123");
        PasswordPolicy.validate("x".repeat(12) + "y1z2");
        assertThatThrownBy(() -> PasswordPolicy.validate("a1".repeat(65))).isInstanceOf(ApiException.class);
    }
}
