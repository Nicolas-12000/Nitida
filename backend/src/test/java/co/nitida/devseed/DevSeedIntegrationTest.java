package co.nitida.devseed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/** The dev profile boots with data a frontend can work against: 2 workshops, 1 admin, 5 motorcycles with signed history. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"nitida.dev.seed-password=una-clave-de-desarrollo-1",
                // its own database: the default test one is shared by every other context in the JVM
                "spring.datasource.url=jdbc:h2:mem:nitida_devseed;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"})
@ActiveProfiles({"test", "dev"})
class DevSeedIntegrationTest {
    @Autowired JdbcClient jdbc;

    private long count(String sql) { return jdbc.sql(sql).query(Long.class).single(); }

    @Test
    void theDevProfileStartsWithWorkshopsAnAdminAndMotorcyclesWithHistory() {
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
                assertThat(count("SELECT count(*) FROM history.history_entry WHERE type = 'SERVICE'")).isEqualTo(5L));

        assertThat(count("SELECT count(*) FROM identity.app_user WHERE platform_admin = true")).isEqualTo(1L);
        assertThat(count("SELECT count(*) FROM workshops.workshop WHERE status = 'VERIFIED' AND name NOT LIKE 'Nítida%'")).isEqualTo(1L);
        assertThat(count("SELECT count(*) FROM workshops.workshop WHERE status = 'PENDING_VERIFICATION'")).isEqualTo(1L);
        assertThat(count("SELECT count(*) FROM motorcycles.motorcycle")).isEqualTo(5L);
        assertThat(count("SELECT count(*) FROM history.history_entry WHERE type = 'INITIAL_REGISTRATION'")).isEqualTo(5L);
    }
}
