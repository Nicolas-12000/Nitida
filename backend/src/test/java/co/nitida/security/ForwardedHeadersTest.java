package co.nitida.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * Behind Next every request reaches the backend from the proxy's address. Per-IP limits must look at the client
 * address the trusted proxy forwards, or one attacker (or one clumsy user) locks login for the whole platform.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"nitida.login.ip-limit=10", "nitida.register.ip-limit=3"})
@ActiveProfiles("test")
class ForwardedHeadersTest {
    @LocalServerPort int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private String xsrf() throws Exception {
        var res = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/me")).build(), HttpResponse.BodyHandlers.ofString());
        return res.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("XSRF-TOKEN=")).findFirst()
                .map(c -> c.substring("XSRF-TOKEN=".length(), c.indexOf(';'))).orElseThrow();
    }

    private int login(String token, String email, String clientIp) throws Exception {
        var req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                .header("Content-Type", "application/json").header("Cookie", "XSRF-TOKEN=" + token).header("X-XSRF-TOKEN", token)
                .header("X-Forwarded-For", clientIp)
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"" + email + "\",\"password\":\"una-clave-larga-123\"}")).build();
        return http.send(req, HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    @Test
    void differentClientsBehindTheProxyDoNotShareTheirLoginBudget() throws Exception {
        String token = xsrf();
        // limit is 10 per IP: 14 different people, one failed attempt each, must never see 429
        for (int i = 0; i < 14; i++) {
            assertThat(login(token, "nadie" + i + "@nitida.test", "203.0.113." + (10 + i))).as("client %d", i).isEqualTo(401);
        }
    }

    private int register(String token, String email, String clientIp) throws Exception {
        var req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/register"))
                .header("Content-Type", "application/json").header("Cookie", "XSRF-TOKEN=" + token).header("X-XSRF-TOKEN", token)
                .header("X-Forwarded-For", clientIp)
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"" + email + "\",\"password\":\"una-clave-larga-123\",\"displayName\":\"Persona\"}")).build();
        return http.send(req, HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    /** Every registration computes an Argon2id hash (memory and CPU on purpose): unlimited, it is the cheapest way to starve the server. */
    @Test
    void registrationIsLimitedPerClientButNotAcrossClients() throws Exception {
        String token = xsrf();
        String tag = java.util.UUID.randomUUID().toString().substring(0, 6);
        for (int i = 0; i < 3; i++) assertThat(register(token, "alta" + tag + i + "@nitida.test", "192.0.2.50")).isEqualTo(201);
        assertThat(register(token, "alta" + tag + "x@nitida.test", "192.0.2.50")).as("fourth from the same client").isEqualTo(429);
        assertThat(register(token, "alta" + tag + "y@nitida.test", "192.0.2.51")).as("another client is unaffected").isEqualTo(201);
    }

    @Test
    void oneClientStillGetsLimitedWhateverItSpoofs() throws Exception {
        String token = xsrf();
        int last = 0;
        for (int i = 0; i < 14; i++) last = login(token, "otro" + i + "@nitida.test", "198.51.100.77");
        assertThat(last).isEqualTo(429);
    }
}
