package co.nitida.devseed;

import co.nitida.shared.Json;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Development data for the frontend: 1 admin, 2 workshops (one verified, one pending) and 5 delivered motorcycles with
 * signed history. It goes through the public HTTP API like any client, so it depends on no module internals and the
 * data is created by the same rules the real flow follows. Only with the {@code dev} profile and a password in
 * {@code nitida.dev.seed-password} (env {@code NITIDA_DEV_SEED_PASSWORD}); no default password exists.
 */
@Component
@Profile("dev")
class DevSeed {
    private static final Logger log = LoggerFactory.getLogger(DevSeed.class);
    static final String ADMIN = "admin@nitida.test";
    static final String OWNER_VERIFIED = "taller.verificado@nitida.test";
    static final String OWNER_PENDING = "taller.pendiente@nitida.test";

    private final JdbcClient jdbc;
    private final Environment env;
    private final String password;

    DevSeed(JdbcClient jdbc, Environment env, @Value("${nitida.dev.seed-password:}") String password) {
        this.jdbc = jdbc;
        this.env = env;
        this.password = password;
    }

    @EventListener(ApplicationReadyEvent.class)
    void seed() {
        if (password.isBlank()) {
            log.info("Dev seed skipped: set NITIDA_DEV_SEED_PASSWORD to create the development data");
            return;
        }
        if (jdbc.sql("SELECT count(*) FROM identity.app_user WHERE email = ?").param(ADMIN).query(Long.class).single() > 0) {
            log.info("Dev seed skipped: already seeded");
            return;
        }
        try {
            var base = "http://localhost:" + env.getRequiredProperty("local.server.port");
            run(base);
            log.info("Dev seed done: 1 admin, 2 workshops, 5 motorcycles with history");
        } catch (RuntimeException e) {
            log.warn("Dev seed failed: {}", e.getMessage()); // dev convenience only: never stops the application
        }
    }

    private void run(String base) {
        var admin = login(base, ADMIN, "Admin Nítida");
        jdbc.sql("UPDATE identity.app_user SET platform_admin = true WHERE email = ?").param(ADMIN).update();
        admin = login(base, ADMIN, null); // the admin role is fixed when the session is created

        var verified = login(base, OWNER_VERIFIED, "Dueño Taller El Rayo");
        UUID ws1 = workshop(verified, "Taller El Rayo", "900123456-7");
        admin.send("POST", "/api/v1/admin/workshops/" + ws1 + "/status", Map.of("status", "VERIFIED", "evidence", "Verificación de datos de desarrollo"), null);

        var pending = login(base, OWNER_PENDING, "Dueña Taller La Chispa");
        workshop(pending, "Taller La Chispa", "900765432-1");

        for (int i = 1; i <= 5; i++) deliveredVisit(verified, ws1, "DEV0" + i + "A", 12_000L * i);
    }

    private Api login(String base, String email, String registerAs) {
        var api = new Api(base);
        api.send("GET", "/api/v1/auth/me", null, null); // 401, but it hands out the CSRF cookie
        if (registerAs != null) api.send("POST", "/api/v1/auth/register", Map.of("email", email, "password", password, "displayName", registerAs), null);
        api.send("POST", "/api/v1/auth/login", Map.of("email", email, "password", password), null);
        return api;
    }

    private UUID workshop(Api owner, String name, String document) {
        var res = owner.send("POST", "/api/v1/workshops", Map.of("name", name, "documentNumber", document, "address", "Calle 18 # 25-10",
                "municipality", "Pasto", "phone", "3101234567"), null);
        return UUID.fromString(res.get("workshopId").asString());
    }

    /** Reception, order, a job and a part, payment in cash and delivery: the history entry follows from the event. */
    private void deliveredVisit(Api owner, UUID ws, String plate, long labor) {
        UUID odometer = owner.upload(ws);
        UUID photo = owner.upload(ws);
        var intake = owner.send("POST", "/api/v1/intakes", Map.of("plate", plate, "brand", "Yamaha", "line", "FZ 2.0", "mileage", 5_000,
                "odometerPhotoId", odometer, "motorcyclePhotoId", photo), ws);
        var order = owner.send("POST", "/api/v1/orders", Map.of("intakeId", intake.get("intakeId").asString(), "diagnosis", "Mantenimiento general de la moto"), ws);
        String id = order.get("orderId").asString();
        owner.send("PATCH", "/api/v1/orders/" + id, Map.of("version", owner.version(id, ws), "laborCost", labor,
                "jobs", List.of(Map.of("type", "OIL_CHANGE", "description", "Cambio de aceite"))), ws);
        owner.send("POST", "/api/v1/orders/" + id + "/parts", Map.of("name", "Filtro de aceite", "brand", "Marca", "condition", "OEM", "quantity", 1, "unitPrice", 15_000), ws);
        owner.send("POST", "/api/v1/orders/" + id + "/transitions", Map.of("status", "IN_PROGRESS", "version", owner.version(id, ws)), ws);
        owner.send("POST", "/api/v1/orders/" + id + "/transitions", Map.of("status", "COMPLETED", "version", owner.version(id, ws)), ws);
        owner.send("POST", "/api/v1/orders/" + id + "/payments", Map.of("method", "CASH", "amount", labor + 15_000), ws);
        owner.send("POST", "/api/v1/orders/" + id + "/delivery", Map.of("version", owner.version(id, ws)), ws);
    }

    /** A browser in miniature: keeps cookies and echoes the CSRF token like the frontend must. */
    private static final class Api {
        private final HttpClient http = HttpClient.newHttpClient();
        private final Map<String, String> cookies = new LinkedHashMap<>();
        private final String base;

        Api(String base) { this.base = base; }

        JsonNode send(String method, String path, Object body, UUID workshop) {
            var req = request(method, path, workshop);
            var publisher = body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(Json.write(body));
            if (body != null) req.header("Content-Type", "application/json");
            return exchange(req.method(method, publisher).build(), path);
        }

        UUID upload(UUID workshop) {
            try {
                var out = new ByteArrayOutputStream();
                ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "jpg", out);
                String boundary = "seed" + UUID.randomUUID();
                var body = new ByteArrayOutputStream();
                body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"p.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                body.write(out.toByteArray());
                body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                var req = request("POST", "/api/v1/files", workshop).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
                return UUID.fromString(exchange(req, "/api/v1/files").get("fileId").asString());
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        }

        long version(String orderId, UUID workshop) {
            return send("GET", "/api/v1/orders/" + orderId, null, workshop).get("version").asLong();
        }

        private HttpRequest.Builder request(String method, String path, UUID workshop) {
            var b = HttpRequest.newBuilder(URI.create(base + path));
            if (!cookies.isEmpty()) b.header("Cookie", String.join("; ", cookies.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList()));
            if (!method.equals("GET") && cookies.containsKey("XSRF-TOKEN")) b.header("X-XSRF-TOKEN", cookies.get("XSRF-TOKEN"));
            if (workshop != null) b.header("X-Workshop-Id", workshop.toString());
            return b;
        }

        private JsonNode exchange(HttpRequest request, String path) {
            try {
                var res = http.send(request, HttpResponse.BodyHandlers.ofString());
                res.headers().allValues("Set-Cookie").forEach(c -> {
                    String pair = c.substring(0, c.indexOf(';') < 0 ? c.length() : c.indexOf(';'));
                    cookies.put(pair.substring(0, pair.indexOf('=')), pair.substring(pair.indexOf('=') + 1));
                });
                boolean expected401 = path.endsWith("/auth/me") && res.statusCode() == 401;
                if (res.statusCode() >= 300 && !expected401) throw new IllegalStateException(request.method() + " " + path + " -> " + res.statusCode() + " " + res.body());
                return res.body().isBlank() ? null : Json.mapper().readTree(res.body());
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }
}
