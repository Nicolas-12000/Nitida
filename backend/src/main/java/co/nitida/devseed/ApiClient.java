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
import tools.jackson.databind.JsonNode;

/**
 * A browser in miniature: keeps cookies and echoes the CSRF token like the frontend must. It speaks only the public
 * HTTP API, so it serves the development seed and the tests that need to drive a running application.
 */
public final class ApiClient {
    public record Visit(String orderId, UUID motorcycleId, long total) {}

    private final HttpClient http = HttpClient.newHttpClient();
    private final Map<String, String> cookies = new LinkedHashMap<>();
    private final String base;

    public ApiClient(String base) { this.base = base; }

    /** CSRF cookie, optional registration, login. A null {@code registerAs} means the account already exists. */
    public static ApiClient signIn(String base, String email, String password, String registerAs) {
        var api = new ApiClient(base);
        api.send("GET", "/api/v1/auth/me", null, null); // 401, but it hands out the CSRF cookie
        if (registerAs != null) api.send("POST", "/api/v1/auth/register", Map.of("email", email, "password", password, "displayName", registerAs), null);
        api.send("POST", "/api/v1/auth/login", Map.of("email", email, "password", password), null);
        return api;
    }

    public JsonNode send(String method, String path, Object body, UUID workshop) {
        var req = request(method, path, workshop);
        var publisher = body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(Json.write(body));
        if (body != null) req.header("Content-Type", "application/json");
        return exchange(req.method(method, publisher).build(), path);
    }

    /** Like {@link #send} but returns the status instead of failing on 4xx, for tests that expect rejections. */
    public int status(String method, String path, Object body, UUID workshop) {
        var req = request(method, path, workshop);
        var publisher = body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(Json.write(body));
        if (body != null) req.header("Content-Type", "application/json");
        return raw(req.method(method, publisher).build()).statusCode();
    }

    public UUID workshop(String name, String document) {
        var res = send("POST", "/api/v1/workshops", Map.of("name", name, "documentNumber", document, "address", "Calle 18 # 25-10",
                "municipality", "Pasto", "phone", "3101234567"), null);
        return UUID.fromString(res.get("workshopId").asString());
    }

    public UUID upload(UUID workshop) {
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

    public long version(String orderId, UUID workshop) {
        return send("GET", "/api/v1/orders/" + orderId, null, workshop).get("version").asLong();
    }

    /** Reception, order, one job and one part (15.000), completed: ready to receive payments. */
    public Visit completedOrder(UUID ws, String plate, long labor) {
        UUID odometer = upload(ws);
        UUID photo = upload(ws);
        var intake = send("POST", "/api/v1/intakes", Map.of("plate", plate, "brand", "Yamaha", "line", "FZ 2.0", "mileage", 5_000,
                "odometerPhotoId", odometer, "motorcyclePhotoId", photo), ws);
        var order = send("POST", "/api/v1/orders", Map.of("intakeId", intake.get("intakeId").asString(), "diagnosis", "Mantenimiento general de la moto"), ws);
        String id = order.get("orderId").asString();
        send("PATCH", "/api/v1/orders/" + id, Map.of("version", version(id, ws), "laborCost", labor,
                "jobs", List.of(Map.of("type", "OIL_CHANGE", "description", "Cambio de aceite"))), ws);
        send("POST", "/api/v1/orders/" + id + "/parts", Map.of("name", "Filtro de aceite", "brand", "Marca", "condition", "OEM", "quantity", 1, "unitPrice", 15_000), ws);
        send("POST", "/api/v1/orders/" + id + "/transitions", Map.of("status", "IN_PROGRESS", "version", version(id, ws)), ws);
        send("POST", "/api/v1/orders/" + id + "/transitions", Map.of("status", "COMPLETED", "version", version(id, ws)), ws);
        return new Visit(id, UUID.fromString(order.get("motorcycleId").asString()), labor + 15_000);
    }

    private HttpRequest.Builder request(String method, String path, UUID workshop) {
        var b = HttpRequest.newBuilder(URI.create(base + path));
        if (!cookies.isEmpty()) b.header("Cookie", String.join("; ", cookies.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList()));
        if (!method.equals("GET") && cookies.containsKey("XSRF-TOKEN")) b.header("X-XSRF-TOKEN", cookies.get("XSRF-TOKEN"));
        if (workshop != null) b.header("X-Workshop-Id", workshop.toString());
        return b;
    }

    private HttpResponse<String> raw(HttpRequest request) {
        try {
            var res = http.send(request, HttpResponse.BodyHandlers.ofString());
            res.headers().allValues("Set-Cookie").forEach(c -> {
                String pair = c.substring(0, c.indexOf(';') < 0 ? c.length() : c.indexOf(';'));
                cookies.put(pair.substring(0, pair.indexOf('=')), pair.substring(pair.indexOf('=') + 1));
            });
            return res;
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private JsonNode exchange(HttpRequest request, String path) {
        var res = raw(request);
        boolean expected401 = path.endsWith("/auth/me") && res.statusCode() == 401;
        if (res.statusCode() >= 300 && !expected401) throw new IllegalStateException(request.method() + " " + path + " -> " + res.statusCode() + " " + res.body());
        return res.body().isBlank() ? null : Json.mapper().readTree(res.body());
    }
}
