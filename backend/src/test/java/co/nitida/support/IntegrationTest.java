package co.nitida.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import co.nitida.identity.application.Mailbox;
import co.nitida.identity.infrastructure.CapturingMailbox;
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
    @Autowired protected CapturingMailbox mailbox;

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

    private static final Map<String, String> TOTP_SECRETS = new java.util.concurrent.ConcurrentHashMap<>();

    /** A platform admin with TOTP enrolled, as the policy requires; the secret is kept for tests that log in again. */
    protected Session registerAdmin() throws Exception {
        String email = uniqueEmail();
        post("/api/v1/auth/register", null, null, Map.of("email", email, "password", PASSWORD, "displayName", "Admin")).andReturn();
        jdbc.sql("UPDATE identity.app_user SET platform_admin = true WHERE email = ?").param(email).update();
        var session = login(email, PASSWORD); // until TOTP is enrolled the session has no admin privileges
        String secret = json(post("/api/v1/auth/totp/setup", session, null, null)).get("secret").asString();
        var code = co.nitida.identity.domain.Totp.code(co.nitida.identity.domain.Base32.decode(secret),
                co.nitida.identity.domain.Totp.stepAt(java.time.Instant.now().getEpochSecond()));
        post("/api/v1/auth/totp/confirm", session, null, Map.of("code", code)).andReturn();
        TOTP_SECRETS.put(email, secret);
        return session;
    }

    protected String totpSecretOf(Session admin) { return TOTP_SECRETS.get(admin.email()); }

    protected UUID createWorkshop(Session owner) throws Exception {
        var r = post("/api/v1/workshops", owner, null, Map.of("name", "Taller El Rayo", "documentNumber", "900123456-7",
                "address", "Calle 18 # 25-10", "municipality", "Pasto", "phone", "3101234567"));
        return UUID.fromString(json(r).get("workshopId").asString());
    }

    /** Invites an email with a role and accepts it with a fresh user; returns that user's session. */
    protected Session addMember(Session owner, UUID workshopId, String role) throws Exception {
        var member = registerAndLogin();
        post("/api/v1/workshops/" + workshopId + "/invitations", owner, workshopId,
                Map.of("email", member.email(), "role", role)).andReturn();
        String token = Mailbox.tokenOf(mailbox.last().text());
        post("/api/v1/invitations/" + token + "/accept", member, null, null).andReturn();
        return member;
    }

    /** Uploads a tiny valid photo through the real endpoint and returns its id. */
    protected UUID uploadPhoto(Session s, UUID workshopId) throws Exception {
        var img = new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "jpg", out);
        var res = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/files")
                .part(new org.springframework.mock.web.MockPart("file", "p.jpg", out.toByteArray()))
                .with(csrf()).cookie(s.cookie()).header("X-Workshop-Id", workshopId.toString()));
        return UUID.fromString(json(res).get("fileId").asString());
    }

    protected void verifyWorkshop(Session admin, UUID workshopId) throws Exception {
        post("/api/v1/admin/workshops/" + workshopId + "/status", admin, null,
                Map.of("status", "VERIFIED", "evidence", "Visita presencial de verificación")).andReturn();
    }
}
