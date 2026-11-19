package co.nitida.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.nitida.identity.application.Mailbox;
import co.nitida.shared.crypto.SecureTokens;
import co.nitida.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Spec section 10: password recovery by mail. Unknown addresses get the same answer and no message. */
class PasswordResetIntegrationTest extends IntegrationTest {

    @Test
    void resetChangesThePasswordAndABadTokenLooksMissing() throws Exception {
        var user = registerAndLogin();
        int before = mailbox.sent().size();
        post("/api/v1/auth/password/forgot", null, null, Map.of("email", "nadie@nitida.test")).andExpect(status().isNoContent());
        assertThat(mailbox.sent()).hasSize(before);

        post("/api/v1/auth/password/forgot", null, null, Map.of("email", user.email())).andExpect(status().isNoContent());
        String token = Mailbox.tokenOf(mailbox.last().text());
        assertThat(mailbox.last().text()).doesNotContain(user.email());
        assertThat(jdbc.sql("SELECT count(*) FROM identity.password_reset WHERE token_hash = ?").param(SecureTokens.hash(token)).query(Long.class).single()).isEqualTo(1L);
        assertThat(jdbc.sql("SELECT count(*) FROM identity.password_reset WHERE token_hash = ?").param(token).query(Long.class).single()).isZero();

        post("/api/v1/auth/password/reset", null, null, Map.of("token", "no-existe", "password", "otra-clave-larga-9")).andExpect(status().isNotFound());
        post("/api/v1/auth/password/reset", null, null, Map.of("token", token, "password", "corta")).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
        post("/api/v1/auth/password/reset", null, null, Map.of("token", token, "password", "otra-clave-larga-9")).andExpect(status().isNoContent());
        post("/api/v1/auth/password/reset", null, null, Map.of("token", token, "password", "otra-clave-larga-9")).andExpect(status().isNotFound());

        post("/api/v1/auth/login", null, null, Map.of("email", user.email(), "password", PASSWORD)).andExpect(status().isUnauthorized());
        post("/api/v1/auth/login", null, null, Map.of("email", user.email(), "password", "otra-clave-larga-9")).andExpect(status().isOk());
    }
}
