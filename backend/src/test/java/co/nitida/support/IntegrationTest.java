package co.nitida.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import co.nitida.shared.Json;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

/** Full-stack tests: real filters, sessions, CSRF, Flyway schema on H2 (PostgreSQL mode). */
@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@AutoConfigureMockMvc
public abstract class IntegrationTest {
    public static final String PASSWORD = "una-clave-larga-123";
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcClient jdbc;

    public record Session(Cookie cookie, UUID userId, String email) {}

    protected static String uniqueEmail() {
        return "user" + SEQ.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 6) + "@nitida.test";
    }

    protected ResultActions call(HttpMethod method, String path, Session session, UUID workshopId, Object body) throws Exception {
        MockHttpServletRequestBuilder b = request(method, path).with(csrf());
        if (session != null) b.cookie(session.cookie());
        if (workshopId != null) b.header("X-Workshop-Id", workshopId.toString());
        if (body != null) b.contentType(MediaType.APPLICATION_JSON).content(Json.write(body));
        return mvc.perform(b);
    }

    protected ResultActions post(String path, Session s, UUID ws, Object body) throws Exception {
        return call(HttpMethod.POST, path, s, ws, body);
    }

    protected ResultActions get(String path, Session s, UUID ws) throws Exception {
        return call(HttpMethod.GET, path, s, ws, null);
    }

    protected JsonNode json(ResultActions r) throws Exception {
        return Json.mapper().readTree(r.andReturn().getResponse().getContentAsString());
    }

    protected Session registerAndLogin() throws Exception {
        String email = uniqueEmail();
        post("/api/v1/auth/register", null, null,
                Map.of("email", email, "password", PASSWORD, "displayName", "Persona de prueba")).andReturn();
        return login(email, PASSWORD);
    }

    protected Session login(String email, String password) throws Exception {
        var res = post("/api/v1/auth/login", null, null, Map.of("email", email, "password", password)).andReturn().getResponse();
        var cookie = res.getCookie("SESSION");
        if (cookie == null) throw new AssertionError("login failed: " + res.getStatus() + " " + res.getContentAsString());
        var id = Json.mapper().readTree(res.getContentAsString()).get("id").asString();
        return new Session(cookie, UUID.fromString(id), email);
    }

    protected Session registerAdmin() throws Exception {
        String email = uniqueEmail();
        post("/api/v1/auth/register", null, null, Map.of("email", email, "password", PASSWORD, "displayName", "Admin")).andReturn();
        jdbc.sql("UPDATE identity.app_user SET platform_admin = true WHERE email = ?").param(email).update();
        return login(email, PASSWORD); // authorities are fixed at login time
    }

    protected UUID createWorkshop(Session owner) throws Exception {
        var r = post("/api/v1/workshops", owner, null, Map.of("name", "Taller El Rayo", "documentNumber", "900123456-7",
                "address", "Calle 18 # 25-10", "municipality", "Pasto", "phone", "3101234567"));
        return UUID.fromString(json(r).get("workshopId").asString());
    }

    /** Invites an email with a role and accepts it with a fresh user; returns that user's session. */
    protected Session addMember(Session owner, UUID workshopId, String role) throws Exception {
        var member = registerAndLogin();
        var inv = post("/api/v1/workshops/" + workshopId + "/invitations", owner, workshopId,
                Map.of("email", member.email(), "role", role));
        String token = json(inv).get("token").asString();
        post("/api/v1/invitations/" + token + "/accept", member, null, null).andReturn();
        return member;
    }
}
