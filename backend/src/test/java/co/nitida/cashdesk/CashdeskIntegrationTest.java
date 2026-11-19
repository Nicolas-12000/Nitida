package co.nitida.cashdesk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.nitida.cashdesk.application.EvaluateScheduledAlertsUseCase;
import co.nitida.cashdesk.domain.BogotaDay;
import co.nitida.support.OrderFlowTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

class CashdeskIntegrationTest extends OrderFlowTest {
    @Autowired EvaluateScheduledAlertsUseCase scheduled;

    private static String today() { return BogotaDay.of(Instant.now()).toString(); }

    private String alertsOf(Shop s, String status) throws Exception {
        return get("/api/v1/cashdesk/alerts?status=" + status, s.owner(), s.ws()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private int countAlerts(Shop s, String type) throws Exception {
        return (int) json(get("/api/v1/cashdesk/alerts?status=PENDING", s.owner(), s.ws())).valueStream()
                .filter(a -> type.equals(a.get("type").asString())).count();
    }

    @Test
    void expectedCashIsOnlyCashPaymentsNotVoidedOfTheDay() throws Exception {
        var s = newShop();
        var v = orderWorth(s, 100_000);
        pay(v, "CASH", 30_000);
        UUID mistaken = pay(v, "CASH", 10_000);
        pay(v, "CARD", 20_000);
        post("/api/v1/payments/" + mistaken + "/void", s.desk(), s.ws(), Map.of("reason", "Se digitó mal el valor")).andExpect(status().isNoContent());
        get("/api/v1/cashdesk/counts/" + today(), s.owner(), s.ws()).andExpect(status().isOk())
                .andExpect(jsonPath("$.expected").value(30_000)).andExpect(jsonPath("$.status").value("OPEN"));
    }

    /** P9 as a seeded random walk: expected always equals the sum of non-voided cash, whatever the mix. */
    @Test
    void expectedAlwaysMatchesTheModelUnderRandomPaymentsAndVoids() throws Exception {
        var s = newShop();
        var v = orderWorth(s, 50_000_000);
        var rnd = new Random(42);
        long model = 0;
        var cashPayments = new java.util.ArrayList<Map.Entry<UUID, Long>>();
        for (int i = 0; i < 25; i++) {
            if (!cashPayments.isEmpty() && rnd.nextInt(4) == 0) {
                var e = cashPayments.remove(rnd.nextInt(cashPayments.size()));
                post("/api/v1/payments/" + e.getKey() + "/void", s.desk(), s.ws(), Map.of("reason", "Anulación aleatoria de prueba")).andExpect(status().isNoContent());
                model -= e.getValue();
            } else {
                long amount = 1 + rnd.nextInt(100_000);
                String method = rnd.nextBoolean() ? "CASH" : "TRANSFER";
                UUID id = pay(v, method, amount);
                if (method.equals("CASH")) {
                    cashPayments.add(Map.entry(id, amount));
                    model += amount;
                }
            }
            get("/api/v1/cashdesk/counts/" + today(), s.owner(), s.ws()).andExpect(jsonPath("$.expected").value(model));
        }
    }

    @Test
    void closingBalancedRaisesNoAlertAndCannotBeEditedUntilReopened() throws Exception {
        var s = newShop();
        pay(orderWorth(s, 100_000), "CASH", 100_000);
        String path = "/api/v1/cashdesk/counts/" + today();
        post(path + "/close", s.owner(), s.ws(), Map.of("counted", 100_000)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BALANCED")).andExpect(jsonPath("$.difference").value(0));
        assertThat(countAlerts(s, "F3_CASH_MISMATCH")).isZero();
        post(path + "/close", s.owner(), s.ws(), Map.of("counted", 90_000)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CASH_COUNT_CLOSED"));
        post(path + "/reopen", s.owner(), s.ws(), Map.of("reason", "corto")).andExpect(status().isUnprocessableContent());
        post(path + "/reopen", s.owner(), s.ws(), Map.of("reason", "Se contó mal el efectivo del día")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPEN"));
        post(path + "/close", s.owner(), s.ws(), Map.of("counted", 90_000)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UNBALANCED"));
        assertThat(jdbc.sql("SELECT count(*) FROM audit.audit_event WHERE workshop_id = ? AND action IN ('CASH_COUNT_CLOSED','CASH_COUNT_REOPENED')")
                .param(s.ws()).query(Long.class).single()).isEqualTo(3L);
    }

    @Test
    void anUnbalancedCloseRaisesAlertF3AndToleranceComesFromTheWorkshopSettings() throws Exception {
        var s = newShop();
        pay(orderWorth(s, 100_000), "CASH", 100_000);
        String ver = json(get("/api/v1/workshops/" + s.ws(), s.owner(), s.ws())).get("intakeAlertHours").asString();
        assertThat(ver).isEqualTo("24");
        call(HttpMethod.PATCH, "/api/v1/workshops/" + s.ws() + "/settings", s.owner(), s.ws(),
                Map.of("intakeAlertHours", 24, "cashTolerance", 5_000, "stalledDays", 7, "version", 0)).andExpect(status().isOk());
        String path = "/api/v1/cashdesk/counts/" + today();
        post(path + "/close", s.owner(), s.ws(), Map.of("counted", 95_000)).andExpect(jsonPath("$.status").value("BALANCED")); // exactly the tolerance
        post(path + "/reopen", s.owner(), s.ws(), Map.of("reason", "Recontar por seguridad del dueño")).andExpect(status().isOk());
        post(path + "/close", s.owner(), s.ws(), Map.of("counted", 94_999)).andExpect(jsonPath("$.status").value("UNBALANCED")); // tolerance + 1
        assertThat(countAlerts(s, "F3_CASH_MISMATCH")).isEqualTo(1);
    }

    @Test
    void onlyTheOwnerHandlesCashAndAlerts() throws Exception {
        var s = newShop();
        String path = "/api/v1/cashdesk/counts/" + today();
        for (var who : new Session[] {s.desk(), s.mechanic()}) {
            get(path, who, s.ws()).andExpect(status().isForbidden());
            post(path + "/close", who, s.ws(), Map.of("counted", 0)).andExpect(status().isForbidden());
            post(path + "/reopen", who, s.ws(), Map.of("reason", "Intento sin permiso de caja")).andExpect(status().isForbidden());
            get("/api/v1/cashdesk/alerts?status=PENDING", who, s.ws()).andExpect(status().isForbidden());
            post("/api/v1/cashdesk/alerts/" + UUID.randomUUID() + "/resolve", who, s.ws(), Map.of("comment", "Intento sin permiso")).andExpect(status().isForbidden());
        }
    }

    @Test
    void futureDaysAndMalformedDaysAreRejected() throws Exception {
        var s = newShop();
        String tomorrow = LocalDate.parse(today()).plusDays(1).toString();
        post("/api/v1/cashdesk/counts/" + tomorrow + "/close", s.owner(), s.ws(), Map.of("counted", 0)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("FUTURE_DAY"));
        get("/api/v1/cashdesk/counts/not-a-date", s.owner(), s.ws()).andExpect(status().isBadRequest());
    }

    @Test
    void cancellingAnOrderWithPartsRaisesF2OnlyForThatWorkshop() throws Exception {
        var s = newShop();
        var other = newShop();
        var v = openOrder(s, 100);
        post("/api/v1/orders/" + v.orderId() + "/parts", s.mechanic(), s.ws(), Map.of("name", "Filtro", "brand", "Marca", "condition", "OEM", "quantity", 1, "unitPrice", 1_000))
                .andExpect(status().isCreated());
        post("/api/v1/orders/" + v.orderId() + "/transitions", s.mechanic(), s.ws(), Map.of("status", "CANCELLED", "reason", "El cliente retiró la moto", "version", version(v)))
                .andExpect(status().isOk());
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(countAlerts(s, "F2_CANCELLED_WITH_PARTS")).isEqualTo(1));
        assertThat(countAlerts(other, "F2_CANCELLED_WITH_PARTS")).isZero();

        var empty = openOrder(s, 100); // no parts: no alert
        post("/api/v1/orders/" + empty.orderId() + "/transitions", s.mechanic(), s.ws(), Map.of("status", "CANCELLED", "reason", "Error de captura", "version", version(empty)))
                .andExpect(status().isOk());
        Thread.sleep(1000);
        assertThat(countAlerts(s, "F2_CANCELLED_WITH_PARTS")).isEqualTo(1);
    }

    @Test
    void aDuplicateSerialRaisesF6ForTheWorkshopThatTriedAndLeaksNothing() throws Exception {
        var s1 = newShop();
        var s2 = newShop();
        String serial = "SN-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
        for (var s : new Shop[] {s1, s2}) {
            var v = openOrder(s, 100);
            UUID part = UUID.fromString(json(post("/api/v1/orders/" + v.orderId() + "/parts", s.mechanic(), s.ws(),
                    Map.of("name", "Batería", "brand", "Marca", "condition", "OEM", "quantity", 1, "unitPrice", 1_000)).andExpect(status().isCreated())).get("partId").asString());
            post("/api/v1/orders/" + v.orderId() + "/parts/" + part + "/serial", s.mechanic(), s.ws(), Map.of("serial", serial)).andExpect(status().isOk());
        }
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(countAlerts(s2, "F6_DUPLICATE_SERIAL")).isEqualTo(1));
        assertThat(countAlerts(s1, "F6_DUPLICATE_SERIAL")).isZero();
        String body = alertsOf(s2, "PENDING");
        assertThat(body).doesNotContain(s1.ws().toString()).doesNotContain(serial);
    }

    @Test
    void theSchedulerRaisesF1F4F5OnceAndIsIdempotent() throws Exception {
        var s = newShop();
        // F1: an intake open for 25 h (threshold 24 h)
        UUID intake = receive(s, nextPlate(), 100);
        jdbc.sql("UPDATE intake.intake SET created_at = ? WHERE id = ?").params(Timestamp.from(Instant.now().minus(Duration.ofHours(25))), intake).update();
        // F5: an order untouched for 8 days (threshold 7)
        var stalled = openOrder(s, 100);
        jdbc.sql("UPDATE orders.work_order SET updated_at = ? WHERE id = ?").params(Timestamp.from(Instant.now().minus(Duration.ofDays(8))), stalled.orderId()).update();
        // F4: a delivered order that ended up at zero total without being a warranty (inconsistent data)
        var zero = orderWorth(s, 10_000);
        pay(zero, "CASH", 10_000);
        deliver(zero);
        jdbc.sql("UPDATE orders.work_order SET labor_cost = 0 WHERE id = ?").param(zero.orderId()).update();

        scheduled.execute();
        scheduled.execute();
        assertThat(countAlerts(s, "F1_INTAKE_WITHOUT_ORDER")).isEqualTo(1);
        assertThat(countAlerts(s, "F5_STALLED_ORDER")).isEqualTo(1);
        assertThat(countAlerts(s, "F4_ZERO_TOTAL_WITHOUT_WARRANTY")).isEqualTo(1);
    }

    @Test
    void recentIntakesAndFreshOrdersRaiseNothing() throws Exception {
        var s = newShop();
        UUID intake = receive(s, nextPlate(), 100);
        jdbc.sql("UPDATE intake.intake SET created_at = ? WHERE id = ?").params(Timestamp.from(Instant.now().minus(Duration.ofHours(23)).minus(Duration.ofMinutes(59))), intake).update();
        openOrder(s, 100);
        scheduled.execute();
        assertThat(alertsOf(s, "PENDING")).isEqualTo("[]");
    }

    @Test
    void alertsAreAttendedOnceWithACommentAndNeverDeleted() throws Exception {
        var s = newShop();
        var other = newShop();
        pay(orderWorth(s, 100_000), "CASH", 100_000);
        post("/api/v1/cashdesk/counts/" + today() + "/close", s.owner(), s.ws(), Map.of("counted", 1)).andExpect(status().isOk());
        String alertId = json(get("/api/v1/cashdesk/alerts?status=PENDING", s.owner(), s.ws())).get(0).get("id").asString();

        post("/api/v1/cashdesk/alerts/" + alertId + "/resolve", other.owner(), other.ws(), Map.of("comment", "Intento desde otro taller")).andExpect(status().isNotFound());
        post("/api/v1/cashdesk/alerts/" + alertId + "/resolve", s.owner(), s.ws(), Map.of("comment", "no")).andExpect(status().isUnprocessableContent());
        post("/api/v1/cashdesk/alerts/" + alertId + "/resolve", s.owner(), s.ws(), Map.of("comment", "Faltante explicado: se pagó un proveedor")).andExpect(status().isNoContent());
        post("/api/v1/cashdesk/alerts/" + alertId + "/resolve", s.owner(), s.ws(), Map.of("comment", "Segundo intento de atender")).andExpect(status().isConflict());
        assertThat(countAlerts(s, "F3_CASH_MISMATCH")).isZero();
        get("/api/v1/cashdesk/alerts?status=RESOLVED", s.owner(), s.ws()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].comment").exists());
        assertThat(jdbc.sql("SELECT count(*) FROM cashdesk.leak_alert WHERE id = ?").param(UUID.fromString(alertId)).query(Long.class).single()).isEqualTo(1L);
    }
}
