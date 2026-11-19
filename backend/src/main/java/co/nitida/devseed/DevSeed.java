package co.nitida.devseed;

import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

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
        signIn(base, ADMIN, "Admin Nítida");
        jdbc.sql("UPDATE identity.app_user SET platform_admin = true WHERE email = ?").param(ADMIN).update();
        var admin = signIn(base, ADMIN, null); // the admin role is fixed when the session is created

        var verified = signIn(base, OWNER_VERIFIED, "Dueño Taller El Rayo");
        UUID ws1 = verified.workshop("Taller El Rayo", "900123456-7");
        admin.send("POST", "/api/v1/admin/workshops/" + ws1 + "/status", Map.of("status", "VERIFIED", "evidence", "Verificación de datos de desarrollo"), null);

        var pending = signIn(base, OWNER_PENDING, "Dueña Taller La Chispa");
        pending.workshop("Taller La Chispa", "900765432-1");

        for (int i = 1; i <= 5; i++) {
            var visit = verified.completedOrder(ws1, "DEV0" + i + "A", 12_000L * i);
            verified.send("POST", "/api/v1/orders/" + visit.orderId() + "/payments", Map.of("method", "CASH", "amount", visit.total()), ws1);
            verified.send("POST", "/api/v1/orders/" + visit.orderId() + "/delivery", Map.of("version", verified.version(visit.orderId(), ws1)), ws1);
        }
    }

    private ApiClient signIn(String base, String email, String registerAs) {
        return ApiClient.signIn(base, email, password, registerAs);
    }
}
