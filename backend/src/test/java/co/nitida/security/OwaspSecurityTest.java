package co.nitida.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.web.servlet.function.RouterFunctions.route;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import co.nitida.support.OrderFlowTest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/** OWASP Top 10:2025 controls that need a running application. The mapping lives in docs/owasp-top10.md. */
class OwaspSecurityTest extends OrderFlowTest {

    /** A route that blows up with an internal detail, to prove errors never leak it (A10). */
    @TestConfiguration
    static class BoomRoute {
        @Bean
        RouterFunction<ServerResponse> boom() {
            return route().GET("/api/v1/test/boom", req -> {
                throw new IllegalStateException("secret-internal-detail jdbc:postgresql://db.internal:5432/nitida password=hunter2");
            }).build();
        }
    }

    // ---- A02 Security Misconfiguration ----

    @Test
    void securityHeadersAreOnEveryResponse() throws Exception {
        var res = get("/api/v1/auth/me", registerAndLogin(), null).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(res.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(res.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(res.getHeader("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
        assertThat(res.getHeader("Content-Security-Policy")).contains("frame-ancestors 'none'").contains("default-src 'none'");
        assertThat(res.getHeader("Cache-Control")).contains("no-store");
    }

    @Test
    void onlyHealthIsPublicAndManagementEndpointsAreClosed() throws Exception {
        for (String path : new String[] {"/actuator/env", "/actuator/beans", "/actuator/heapdump", "/actuator/mappings", "/actuator/loggers", "/swagger-ui.html", "/v3/api-docs", "/h2-console"}) {
            assertThat(get(path, null, null).andReturn().getResponse().getStatus()).as("anonymous %s", path).isIn(401, 403, 404);
            assertThat(get(path, registerAndLogin(), null).andReturn().getResponse().getStatus()).as("logged-in %s", path).isIn(401, 403, 404);
        }
    }

    @Test
    void theSessionCookieIsHttpOnlyAndSameSiteLax() throws Exception {
        var s = registerAndLogin();
        var res = post("/api/v1/auth/login", null, null, Map.of("email", s.email(), "password", PASSWORD)).andReturn().getResponse();
        assertThat(res.getHeaders("Set-Cookie")).as("Set-Cookie headers").anySatisfy(c -> assertThat(c).startsWith("SESSION=").contains("HttpOnly").contains("SameSite=Lax"));
    }

    // ---- A04 Cryptographic Failures ----

    @Test
    void passwordsAreArgon2idAndTokensAreStoredOnlyAsHashes() throws Exception {
        var s = registerAndLogin();
        assertThat(jdbc.sql("SELECT password_hash FROM identity.app_user WHERE id = ?").param(s.userId()).query(String.class).single())
                .startsWith("$argon2id$").doesNotContain(PASSWORD);

        var shop = newShop();
        var v = orderWorth(shop, 10_000);
        pay(v, "CASH", 10_000);
        String claim = deliver(v);
        assertThat(jdbc.sql("SELECT code_hash FROM motorcycles.claim_code WHERE motorcycle_id = ?").param(v.motorcycleId()).query(String.class).single())
                .isNotEqualTo(claim).hasSize(64);

        String link = json(post("/api/v1/motorcycles/" + v.motorcycleId() + "/links", shop.owner(), shop.ws(), Map.of("days", 5)).andExpect(status().isCreated())).get("token").asString();
        assertThat(jdbc.sql("SELECT count(*) FROM history.public_link WHERE token_hash = ?").param(link).query(Long.class).single()).isZero();
        assertThat(link).hasSize(22); // 128 bits, base64url without padding (RN-71)
    }

    // ---- A05 Injection ----

    @Test
    void injectionPayloadsAreTreatedAsDataEverywhere() throws Exception {
        var shop = newShop();
        var customer = new HashMap<String, Object>();
        customer.put("fullName", "Ana Gómez");
        customer.put("phone", "3101234567");
        customer.put("consentAccepted", true);
        customer.put("policyVersion", "2026-1");
        customer.put("consentChannel", "IN_PERSON");
        post("/api/v1/customers", shop.owner(), shop.ws(), customer).andExpect(status().isCreated());

        for (String payload : new String[] {"' OR '1'='1", "'; DROP TABLE motorcycles.customer; --", "%' OR 1=1 --", "<script>alert(1)</script>", "\\u0000", "ana' UNION SELECT * FROM identity.app_user --"}) {
            var res = call(HttpMethod.GET, "/api/v1/customers", shop.owner(), shop.ws(), null).andReturn().getResponse().getStatus();
            assertThat(res).isEqualTo(422); // no query: rejected, not executed
            var found = get("/api/v1/customers?q=" + java.net.URLEncoder.encode(payload, java.nio.charset.StandardCharsets.UTF_8), shop.owner(), shop.ws())
                    .andReturn().getResponse();
            assertThat(found.getStatus()).as("payload %s", payload).isIn(200, 422);
            if (found.getStatus() == 200) assertThat(found.getContentAsString()).as("payload %s must not match everything", payload).isEqualTo("[]");
            assertThat(get("/api/v1/motorcycles/lookup?plate=" + java.net.URLEncoder.encode(payload, java.nio.charset.StandardCharsets.UTF_8), shop.owner(), shop.ws())
                    .andReturn().getResponse().getStatus()).as("plate payload %s", payload).isBetween(400, 499);
        }
        assertThat(jdbc.sql("SELECT count(*) FROM motorcycles.customer WHERE workshop_id = ?").param(shop.ws()).query(Long.class).single()).isEqualTo(1L);
    }

    // ---- A06 Insecure Design ----

    @Test
    void historyCanOnlyGrowNoEditNorDeleteExists() throws Exception {
        var shop = newShop();
        var v = orderWorth(shop, 10_000);
        pay(v, "CASH", 10_000);
        deliver(v);
        for (HttpMethod m : new HttpMethod[] {HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE}) {
            for (String path : new String[] {"/api/v1/motorcycles/" + v.motorcycleId() + "/history", "/api/v1/motorcycles/" + v.motorcycleId() + "/history/1"}) {
                assertThat(call(m, path, shop.owner(), shop.ws(), Map.of()).andReturn().getResponse().getStatus()).as("%s %s", m, path).isBetween(400, 499);
            }
        }
    }

    // ---- A09 Logging and Alerting Failures ----

    @Test
    void logsNeverCarryPersonalDataSecretsOrCodes() throws Exception {
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        var root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.addAppender(appender);
        try {
            String email = uniqueEmail();
            post("/api/v1/auth/register", null, null, Map.of("email", email, "password", "una-clave-secreta-777", "displayName", "Persona Privada")).andReturn();
            post("/api/v1/auth/login", null, null, Map.of("email", email, "password", "una-clave-equivocada-1")).andReturn();
            var owner = login(email, "una-clave-secreta-777");
            UUID ws = createWorkshop(owner);
            var customer = new HashMap<String, Object>();
            customer.put("fullName", "Cliente Reservado");
            customer.put("phone", "3207654321");
            customer.put("nationalId", "1085123456");
            customer.put("email", "cliente.reservado@correo.test");
            customer.put("consentAccepted", true);
            customer.put("policyVersion", "2026-1");
            customer.put("consentChannel", "IN_PERSON");
            post("/api/v1/customers", owner, ws, customer).andReturn();
            get("/api/v1/orders/not-a-uuid", owner, ws).andReturn(); // an error path as well

            var shop = newShop();
            var v = orderWorth(shop, 10_000);
            pay(v, "CASH", 10_000);
            String claim = deliver(v);
            Thread.sleep(500); // let async listeners log too

            String logs = String.join("\n", appender.list.stream().map(e -> e.getFormattedMessage()
                    + (e.getThrowableProxy() == null ? "" : " " + e.getThrowableProxy().getMessage())).toList());
            for (String secret : new String[] {email, "una-clave-secreta-777", "una-clave-equivocada-1", "Persona Privada", "Cliente Reservado", "3207654321",
                    "1085123456", "cliente.reservado@correo.test", "3101234567", "900123456-7", claim}) {
                assertThat(logs).as("logs must not contain %s", secret).doesNotContain(secret);
            }
        } finally {
            root.detachAppender(appender);
        }
    }

    // ---- A10 Mishandling of Exceptional Conditions ----

    @Test
    void anInternalFailureAnswersAGenericProblemWithoutDetails() throws Exception {
        var res = get("/api/v1/test/boom", registerAndLogin(), null).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andExpect(jsonPath("$.traceId").exists()).andReturn().getResponse();
        assertThat(res.getContentType()).startsWith("application/problem+json");
        assertThat(res.getContentAsString()).doesNotContain("secret-internal-detail").doesNotContain("jdbc:").doesNotContain("hunter2")
                .doesNotContain("IllegalStateException").doesNotContain("at co.nitida").doesNotContain("stackTrace");
    }

    @Test
    void malformedInputIsA4xxProblemNeverAStackTrace() throws Exception {
        var s = registerAndLogin();
        for (String path : new String[] {"/api/v1/orders/not-a-uuid", "/api/v1/cashdesk/counts/31-12-2026", "/api/v1/motorcycles/%00/history"}) {
            var res = get(path, s, createWorkshop(s)).andReturn().getResponse();
            assertThat(res.getStatus()).as(path).isBetween(400, 499);
            assertThat(res.getContentAsString()).as(path).doesNotContain("Exception").doesNotContain("at co.nitida").doesNotContain("at org.springframework");
        }
        var bad = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{not json")).andReturn().getResponse();
        assertThat(bad.getStatus()).isEqualTo(400);
        assertThat(bad.getContentAsString()).doesNotContain("Exception").doesNotContain("Jackson").doesNotContain("at tools.jackson");
    }
}
