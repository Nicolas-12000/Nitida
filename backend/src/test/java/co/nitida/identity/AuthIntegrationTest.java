package co.nitida.identity;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.nitida.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class AuthIntegrationTest extends IntegrationTest {

    @Test
    void registerThenLoginThenMe() throws Exception {
        String email = uniqueEmail();
        post("/api/v1/auth/register", null, null, Map.of("email", email, "password", PASSWORD, "displayName", "Ana"))
                .andExpect(status().isCreated());
        var s = login(email, PASSWORD);
        get("/api/v1/auth/me", s, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email)).andExpect(jsonPath("$.platformAdmin").value(false));
    }

    @Test
    void emailIsCaseInsensitiveAndDuplicatesConflict() throws Exception {
        String email = uniqueEmail();
        post("/api/v1/auth/register", null, null, Map.of("email", email.toUpperCase(), "password", PASSWORD, "displayName", "Ana"))
                .andExpect(status().isCreated());
        post("/api/v1/auth/register", null, null, Map.of("email", email, "password", PASSWORD, "displayName", "Ana"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void weakPasswordAndBadEmailAreUnprocessable() throws Exception {
        post("/api/v1/auth/register", null, null, Map.of("email", uniqueEmail(), "password", "corta", "displayName", "Ana"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
        post("/api/v1/auth/register", null, null, Map.of("email", "no-es-correo", "password", PASSWORD, "displayName", "Ana"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INVALID_EMAIL"));
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSameAnswer() throws Exception {
        var s = registerAndLogin();
        var wrong = post("/api/v1/auth/login", null, null, Map.of("email", s.email(), "password", "otra-clave-larga-1"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        var unknown = post("/api/v1/auth/login", null, null, Map.of("email", "nadie@nitida.test", "password", PASSWORD))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        var mapper = co.nitida.shared.Json.mapper();
        org.assertj.core.api.Assertions.assertThat(mapper.readTree(wrong).get("code"))
                .isEqualTo(mapper.readTree(unknown).get("code"));
        org.assertj.core.api.Assertions.assertThat(mapper.readTree(wrong).get("title"))
                .isEqualTo(mapper.readTree(unknown).get("title"));
    }

    @Test
    void accountLocksAfterRepeatedFailures() throws Exception {
        var s = registerAndLogin();
        for (int i = 0; i < 5; i++) {
            post("/api/v1/auth/login", null, null, Map.of("email", s.email(), "password", "mala-clave-larga-1"))
                    .andExpect(status().isUnauthorized());
        }
        post("/api/v1/auth/login", null, null, Map.of("email", s.email(), "password", PASSWORD))
                .andExpect(status().isUnauthorized()); // correct password, still locked
    }

    @Test
    void protectedEndpointRequiresLogin() throws Exception {
        get("/api/v1/auth/me", null, null).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void stateChangingRequestWithoutCsrfTokenIsForbidden() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x@y.co\",\"password\":\"una-clave-larga-123\",\"displayName\":\"X\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        var s = registerAndLogin();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/logout").with(csrf()).cookie(s.cookie())).andExpect(status().isNoContent());
        get("/api/v1/auth/me", s, null).andExpect(status().isUnauthorized());
    }

    @Test
    void malformedJsonIsProblemDetailsNotAStackTrace() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{nope"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }
}
