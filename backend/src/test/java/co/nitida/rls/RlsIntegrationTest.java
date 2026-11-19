package co.nitida.rls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.nitida.cashdesk.application.EvaluateScheduledAlertsUseCase;
import co.nitida.orders.domain.WorkOrderRepository;
import co.nitida.shared.tenancy.TenantSession;
import co.nitida.support.OrderFlowTest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Spec section 8.2, second barrier: PostgreSQL itself keeps one workshop's rows from another. Runs on a real
 * PostgreSQL (Testcontainers) with the real migrations: Flyway connects as the table owner, the application as a
 * plain role without BYPASSRLS, exactly like docker compose.
 */
@Testcontainers(disabledWithoutDocker = true)
class RlsIntegrationTest extends OrderFlowTest {
    @Container
    static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:17-alpine").withInitScript("rls/init-roles.sql");

    private static final String APP_USER = "nitida_app";
    private static final String APP_PASSWORD = "app-test-password";

    /** Tables with a workshop_id that are deliberately NOT row-secured, and why (spec section 8.2). */
    private static final Set<String> GLOBAL_OR_PRE_CONTEXT = Set.of(
            "identity.membership", "identity.invitation", // read before the workshop is known: they decide who may enter it
            "workshops.signing_key", // signing runs from async listeners
            "history.history_entry", "history.public_link", "catalog.serial", // global by design, no personal data (RN-22, RN-94)
            "audit.audit_event"); // insert-only trail written from every context

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", () -> APP_USER);
        r.add("spring.datasource.password", () -> APP_PASSWORD);
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        r.add("spring.flyway.user", PG::getUsername);
        r.add("spring.flyway.password", PG::getPassword);
        r.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/postgres");
        r.add("nitida.rls.enabled", () -> "true");
    }

    @Autowired WorkOrderRepository orders;
    @Autowired TenantSession tenant;
    @Autowired TransactionTemplate tx;
    @Autowired EvaluateScheduledAlertsUseCase scheduled;

    private static Connection app() throws SQLException { return DriverManager.getConnection(PG.getJdbcUrl(), APP_USER, APP_PASSWORD); }

    private static Connection owner() throws SQLException { return DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()); }

    private static long count(Connection c, String sql, Object... params) throws SQLException {
        try (var ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (var rs = ps.executeQuery()) { rs.next(); return rs.getLong(1); }
        }
    }

    private static void asWorkshop(Connection c, UUID ws) throws SQLException {
        try (var ps = c.prepareStatement("select set_config('app.workshop_id', ?, false)")) { ps.setString(1, ws.toString()); ps.executeQuery(); }
    }

    @Test
    void theApplicationRoleOwnsNothingAndCannotBypassRls() throws Exception {
        try (var c = app()) {
            assertThat(count(c, "select count(*) from pg_roles where rolname = current_user and (rolsuper or rolbypassrls or rolcreaterole or rolcreatedb)")).isZero();
            assertThat(count(c, "select count(*) from pg_tables where tableowner = current_user")).as("tables owned by the application role").isZero();
        }
    }

    @Test
    void everyWorkshopTableHasRlsAndOnlyTheDocumentedOnesDoNot() throws Exception {
        try (var c = owner(); var st = c.createStatement()) {
            var withRls = new TreeSet<String>();
            try (var rs = st.executeQuery("select n.nspname || '.' || c.relname from pg_class c join pg_namespace n on n.oid = c.relnamespace where c.relrowsecurity")) {
                while (rs.next()) withRls.add(rs.getString(1));
            }
            var withWorkshopColumn = new TreeSet<String>();
            try (var rs = st.executeQuery("select table_schema || '.' || table_name from information_schema.columns where column_name = 'workshop_id' "
                    + "and table_schema not in ('information_schema','pg_catalog')")) {
                while (rs.next()) withWorkshopColumn.add(rs.getString(1));
            }
            withWorkshopColumn.removeAll(GLOBAL_OR_PRE_CONTEXT);
            assertThat(withRls).as("tables with row-level security").isEqualTo(withWorkshopColumn);
            assertThat(withRls).hasSizeGreaterThanOrEqualTo(8);
        }
    }

    @Test
    void withoutAWorkshopTheApplicationSeesNothingAndEachWorkshopSeesOnlyItsOwn() throws Exception {
        var a = newShop();
        var b = newShop();
        var orderA = openOrder(a, 100);
        var orderB = openOrder(b, 100);

        try (var o = owner(); var c = app()) {
            assertThat(count(o, "select count(*) from orders.work_order where id in (?, ?)", orderA.orderId(), orderB.orderId())).isEqualTo(2);
            assertThat(count(c, "select count(*) from orders.work_order")).as("no workshop selected: nothing visible").isZero();

            asWorkshop(c, a.ws());
            assertThat(count(c, "select count(*) from orders.work_order where id = ?", orderA.orderId())).isEqualTo(1);
            assertThat(count(c, "select count(*) from orders.work_order where id = ?", orderB.orderId())).as("a foreign id, asked by hand").isZero();
            assertThat(count(c, "select count(*) from intake.intake where id = ?", orderB.intakeId())).isZero();
            assertThat(count(c, "select count(*) from orders.work_order where workshop_id = ?", b.ws())).as("even filtering by the other workshop").isZero();

            try (var ps = c.prepareStatement("update orders.work_order set diagnosis = 'tampered' where id = ?")) {
                ps.setObject(1, orderB.orderId());
                assertThat(ps.executeUpdate()).as("rows another workshop could modify").isZero();
            }
            try (var ps = c.prepareStatement("delete from orders.work_order where id = ?")) {
                ps.setObject(1, orderB.orderId());
                assertThat(ps.executeUpdate()).as("rows another workshop could delete").isZero();
            }

            asWorkshop(c, b.ws());
            assertThat(count(c, "select count(*) from orders.work_order where id = ?", orderB.orderId())).isEqualTo(1);
            assertThat(count(c, "select count(*) from orders.work_order where id = ?", orderA.orderId())).isZero();
        }
        assertThat(count(owner(), "select count(*) from orders.work_order where id = ? and diagnosis = 'tampered'", orderB.orderId())).isZero();
    }

    @Test
    void aWorkshopCannotWriteRowsForAnotherOne() throws Exception {
        var a = newShop();
        var b = newShop();
        String insert = "insert into cashdesk.leak_alert(id, workshop_id, type, dedupe_key, details, created_at) values (?, ?, 'F1_INTAKE_WITHOUT_ORDER', ?, 'x', now())";
        try (var c = app()) {
            asWorkshop(c, a.ws());
            try (var ps = c.prepareStatement(insert)) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, b.ws());
                ps.setString(3, "k-" + UUID.randomUUID());
                assertThatThrownBy(ps::executeUpdate).isInstanceOf(SQLException.class).hasMessageContaining("row-level security");
            }
            try (var ps = c.prepareStatement(insert)) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, a.ws());
                ps.setString(3, "k-" + UUID.randomUUID());
                assertThat(ps.executeUpdate()).isEqualTo(1);
            }
        }
    }

    @Test
    void aRepositoryThatForgotItsWorkshopFilterStillCannotLeak() throws Exception {
        var a = newShop();
        var b = newShop();
        var ofA = openOrder(a, 100).orderId();
        openOrder(b, 100);
        var seen = tx.execute(s -> {
            tenant.use(a.ws());
            return orders.findAll().stream().map(o -> o.getWorkshopId()).distinct().toList();
        });
        assertThat(seen).as("findAll() has no workshop filter at all").containsExactly(a.ws());
        Boolean visible = tx.execute(s -> orders.findById(ofA).isPresent());
        assertThat(visible).as("no workshop selected").isFalse();
    }

    @Test
    void theWholeOrderFlowWorksUnderRlsIncludingTheAsyncHistory() throws Exception {
        var s = newShop();
        var v = orderWorth(s, 50_000);
        pay(v, "CASH", 50_000);
        String claim = deliver(v);
        assertThat(claim).hasSize(8);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(count(app(), "select count(*) from history.history_entry where motorcycle_id = ?", v.motorcycleId())).isEqualTo(2));
        get("/api/v1/orders/" + v.orderId(), s.owner(), s.ws()).andExpect(status().isOk());
    }

    @Test
    void listenersAndTheSchedulerWorkWithoutAnHttpRequest() throws Exception {
        var s = newShop();
        // a listener: cancelling an order with parts raises F2 from an event, with no request on the thread
        var v = openOrder(s, 100);
        post("/api/v1/orders/" + v.orderId() + "/parts", s.mechanic(), s.ws(), Map.of("name", "Filtro", "brand", "Marca", "condition", "OEM", "quantity", 1, "unitPrice", 1_000)).andExpect(status().isCreated());
        post("/api/v1/orders/" + v.orderId() + "/transitions", s.mechanic(), s.ws(), Map.of("status", "CANCELLED", "reason", "El cliente retiró la moto", "version", version(v))).andExpect(status().isOk());
        // the scheduler: sees every workshop, raises F5 for a stalled order
        var stalled = openOrder(s, 100);
        try (var o = owner(); var ps = o.prepareStatement("update orders.work_order set updated_at = now() - interval '8 days' where id = ?")) {
            ps.setObject(1, stalled.orderId());
            ps.executeUpdate();
        }
        scheduled.execute();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            try (var o = owner()) {
                assertThat(count(o, "select count(*) from cashdesk.leak_alert where workshop_id = ? and type = 'F2_CANCELLED_WITH_PARTS'", s.ws())).isEqualTo(1);
                assertThat(count(o, "select count(*) from cashdesk.leak_alert where workshop_id = ? and type = 'F5_STALLED_ORDER'", s.ws())).isEqualTo(1);
            }
        });
        get("/api/v1/cashdesk/alerts?status=PENDING", s.owner(), s.ws()).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void theAuditTrailAndTheHistoryAreAppendOnlyForTheApplication() throws Exception {
        newShop(); // writes audit rows and, through the app role, proves INSERT works
        try (var c = app(); var st = c.createStatement()) {
            assertThat(count(c, "select count(*) from audit.audit_event")).isPositive();
            assertThatThrownBy(() -> st.executeUpdate("update audit.audit_event set action = 'x'")).hasMessageContaining("permission denied");
            assertThatThrownBy(() -> st.executeUpdate("delete from audit.audit_event")).hasMessageContaining("permission denied");
            assertThatThrownBy(() -> st.executeUpdate("update history.history_entry set source = 'x'")).hasMessageContaining("permission denied");
            assertThatThrownBy(() -> st.executeUpdate("delete from history.history_entry")).hasMessageContaining("permission denied");
        }
    }

    // ---- concurrency on a real PostgreSQL (spec section 15.6) ----

    @Test
    void twoWorkshopsRacingForTheSameSerialLeaveExactlyOneWinner() throws Exception {
        var shops = List.of(newShop(), newShop());
        String serial = "RACE-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
        var targets = new ArrayList<Runnable>();
        var accepted = java.util.Collections.synchronizedList(new ArrayList<Boolean>());
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        var futures = new ArrayList<java.util.concurrent.Future<?>>();
        for (var s : shops) {
            var v = openOrder(s, 100);
            UUID part = UUID.fromString(json(post("/api/v1/orders/" + v.orderId() + "/parts", s.mechanic(), s.ws(),
                    Map.of("name", "Batería", "brand", "Marca", "condition", "OEM", "quantity", 1, "unitPrice", 1_000)).andExpect(status().isCreated())).get("partId").asString());
            futures.add(pool.submit(() -> {
                start.await();
                var body = co.nitida.shared.Json.mapper().readTree(post("/api/v1/orders/" + v.orderId() + "/parts/" + part + "/serial", s.mechanic(), s.ws(),
                        Map.of("serial", serial)).andReturn().getResponse().getContentAsString());
                accepted.add(body.get("accepted").asBoolean());
                return null;
            }));
        }
        start.countDown();
        for (var f : futures) f.get();
        pool.shutdown();
        assertThat(accepted).containsExactlyInAnyOrder(true, false);
        assertThat(count(owner(), "select count(*) from catalog.serial where normalized_serial = ? and status = 'ACTIVE'", serial)).isEqualTo(1);
    }

    @Test
    void simultaneousPaymentsCannotExceedTheTotalOnRealPostgres() throws Exception {
        var s = newShop();
        var v = orderWorth(s, 100_000);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(6);
        var results = new ArrayList<java.util.concurrent.Future<Integer>>();
        for (int i = 0; i < 6; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return post("/api/v1/orders/" + v.orderId() + "/payments", s.desk(), s.ws(), Map.of("method", "CASH", "amount", 100_000)).andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        int created = 0;
        for (var f : results) if (f.get() == 201) created++;
        pool.shutdown();
        assertThat(created).isEqualTo(1);
    }
}
