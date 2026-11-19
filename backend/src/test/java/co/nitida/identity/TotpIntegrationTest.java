package co.nitida.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.nitida.identity.domain.Base32;
import co.nitida.identity.domain.Totp;
import co.nitida.support.IntegrationTest;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/** Spec §10: TOTP is mandatory for the platform admin and optional for everyone else. */
class TotpIntegrationTest extends IntegrationTest {

    private static String codeFor(String secret, long stepOffset) {
        return Totp.code(Base32.decode(secret), Totp.stepAt(Instant.now().getEpochSecond()) + stepOffset);
    }

    private org.springframework.test.web.servlet.ResultActions loginWith(String email, String code) throws Exception {
        var body = new java.util.HashMap<String, Object>();
        body.put("email", email);
        body.put("password", PASSWORD);
        if (code != null) body.put("totp", code);
        return post("/api/v1/auth/login", null, null, body);
    }

    @Test
    void anAdminWithoutTotpCanLogInButCannotUseAdminEndpointsUntilEnrolling() throws Exception {
        String email = uniqueEmail();
        post("/api/v1/auth/register", null, null, Map.of("email", email, "password", PASSWORD, "displayName", "Admin")).andReturn();
        jdbc.sql("UPDATE identity.app_user SET platform_admin = true WHERE email = ?").param(email).update();
        var s = login(email, PASSWORD);

        get("/api/v1/auth/me", s, null).andExpect(status().isOk()).andExpect(jsonPath("$.platformAdmin").value(true))
                .andExpect(jsonPath("$.totpEnabled").value(false)).andExpect(jsonPath("$.totpSetupRequired").value(true));
        get("/api/v1/admin/workshops", s, null).andExpect(status().isForbidden());

        String secret = json(post("/api/v1/auth/totp/setup", s, null, null).andExpect(status().isOk())).get("secret").asString();
        post("/api/v1/auth/totp/confirm", s, null, Map.of("code", "000000")).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("INVALID_TOTP"));
        get("/api/v1/admin/workshops", s, null).andExpect(status().isForbidden());

        post("/api/v1/auth/totp/confirm", s, null, Map.of("code", codeFor(secret, 0))).andExpect(status().isNoContent());
        get("/api/v1/admin/workshops", s, null).andExpect(status().isOk()); // same session, privileges granted now
        get("/api/v1/auth/me", s, null).andExpect(jsonPath("$.totpEnabled").value(true)).andExpect(jsonPath("$.totpSetupRequired").value(false));
    }

    @Test
    void onceEnabledLoginNeedsAFreshValidCode() throws Exception {
        var admin = registerAdmin();
        String secret = totpSecretOf(admin);

        loginWith(admin.email(), null).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("TOTP_REQUIRED"));
        loginWith(admin.email(), "000000").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        String code = codeFor(secret, 1); // newer than the one used to enroll, still inside the window
        var res = loginWith(admin.email(), code).andExpect(status().isOk()).andReturn().getResponse();
        var session = new Session(res.getCookie("SESSION"), admin.userId(), admin.email());
        get("/api/v1/admin/workshops", session, null).andExpect(status().isOk());

        loginWith(admin.email(), code).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS")); // replay
    }

    @Test
    void aWrongPasswordNeverRevealsWhetherTotpIsEnabled() throws Exception {
        var admin = registerAdmin();
        var wrong = post("/api/v1/auth/login", null, null, Map.of("email", admin.email(), "password", "otra-clave-larga-1")).andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertThat(wrong).contains("INVALID_CREDENTIALS").doesNotContain("TOTP");
    }

    @Test
    void anyoneMayEnrollAndThenNeedsTheCodeToo() throws Exception {
        var user = registerAndLogin();
        assertThat(json(get("/api/v1/auth/me", user, null)).get("totpSetupRequired").asBoolean()).as("optional for ordinary users").isFalse();
        String secret = json(post("/api/v1/auth/totp/setup", user, null, null).andExpect(status().isOk())).get("secret").asString();
        post("/api/v1/auth/totp/confirm", user, null, Map.of("code", codeFor(secret, 0))).andExpect(status().isNoContent());
        loginWith(user.email(), null).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("TOTP_REQUIRED"));
        loginWith(user.email(), codeFor(secret, 1)).andExpect(status().isOk());
    }

    @Test
    void enrollingTwiceIsAConflictAndRequiresALogin() throws Exception {
        var admin = registerAdmin();
        post("/api/v1/auth/totp/setup", admin, null, null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TOTP_ALREADY_ENABLED"));
        call(HttpMethod.POST, "/api/v1/auth/totp/setup", null, null, null).andExpect(status().isUnauthorized());
        call(HttpMethod.POST, "/api/v1/auth/totp/confirm", null, null, Map.of("code", "123456")).andExpect(status().isUnauthorized());
    }

    @Test
    void theSecretIsStoredEncryptedNeverInClear() throws Exception {
        var admin = registerAdmin();
        String secret = totpSecretOf(admin);
        byte[] stored = jdbc.sql("SELECT totp_secret FROM identity.app_user WHERE id = ?").param(admin.userId()).query(byte[].class).single();
        assertThat(stored).isNotEqualTo(Base32.decode(secret));
        assertThat(new String(stored, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain(secret);
    }

    @Test
    void confirmingWithoutSettingUpFirstIsRejected() throws Exception {
        var user = registerAndLogin();
        post("/api/v1/auth/totp/confirm", user, null, Map.of("code", "123456")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TOTP_NOT_STARTED"));
    }

    @Test
    void guessingTheEnrollmentCodeIsLimited() throws Exception {
        var user = registerAndLogin();
        post("/api/v1/auth/totp/setup", user, null, null).andExpect(status().isOk());
        for (int i = 0; i < 5; i++) post("/api/v1/auth/totp/confirm", user, null, Map.of("code", "00000" + i)).andExpect(status().isUnprocessableContent());
        post("/api/v1/auth/totp/confirm", user, null, Map.of("code", "000009")).andExpect(status().isTooManyRequests());
    }
}
