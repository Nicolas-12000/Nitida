package co.nitida.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.nitida.history.HistoryApi;
import co.nitida.support.IntegrationTest;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.ResultActions;

/** The whole life of an order through the real HTTP stack, sessions, CSRF, roles and events. */
class OrdersIntegrationTest extends IntegrationTest {
    @Autowired HistoryApi history;

    private static int seq = 0;

    record Shop(Session owner, Session mechanic, Session desk, UUID ws) {}

    record Visit(Shop shop, UUID intakeId, UUID motorcycleId, UUID orderId) {}

    private Shop shop() throws Exception {
        var owner = registerAndLogin();
        UUID ws = createWorkshop(owner);
        return new Shop(owner, addMember(owner, ws, "MECHANIC"), addMember(owner, ws, "FRONT_DESK"), ws);
    }

    private UUID intake(Shop s, String plate, int km) throws Exception {
        var body = new HashMap<String, Object>();
        body.put("plate", plate);
        body.put("brand", "Yamaha");
        body.put("line", "FZ 2.0");
        body.put("mileage", km);
        body.put("odometerPhotoId", uploadPhoto(s.owner(), s.ws()));
        body.put("motorcyclePhotoId", uploadPhoto(s.owner(), s.ws()));
        return UUID.fromString(json(post("/api/v1/intakes", s.desk(), s.ws(), body).andExpect(status().isCreated())).get("intakeId").asString());
    }

    private String plate() {
        int n = seq++;
        return "OR" + (char) ('A' + (n / 100) % 26) + String.format("%02d", n % 100) + "Q";
    }

    private Visit visit(Shop s, int km) throws Exception {
        var intakeId = intake(s, plate(), km);
        var res = json(post("/api/v1/orders", s.mechanic(), s.ws(), Map.of("intakeId", intakeId, "diagnosis", "Ruido en la transmisión al acelerar"))
                .andExpect(status().isCreated()));
        return new Visit(s, intakeId, UUID.fromString(res.get("motorcycleId").asString()), UUID.fromString(res.get("orderId").asString()));
    }

    private long version(Visit v) throws Exception {
        return json(get("/api/v1/orders/" + v.orderId(), v.shop().owner(), v.shop().ws())).get("version").asLong();
    }

    private ResultActions transition(Visit v, Session who, String target, String reason) throws Exception {
        var body = new HashMap<String, Object>();
        body.put("status", target);
        body.put("reason", reason);
        body.put("version", version(v));
        return post("/api/v1/orders/" + v.orderId() + "/transitions", who, v.shop().ws(), body);
    }

    private void addJob(Visit v, long laborCost) throws Exception {
        call(HttpMethod.PATCH, "/api/v1/orders/" + v.orderId(), v.shop().mechanic(), v.shop().ws(),
                Map.of("version", version(v), "laborCost", laborCost,
                        "jobs", List.of(Map.of("type", "DRIVE_CHAIN_KIT", "description", "Cambio de kit de arrastre")))).andExpect(status().isNoContent());
    }

    private UUID addPart(Visit v, long unitPrice, String code) throws Exception {
        var body = new HashMap<String, Object>();
        body.put("name", "Kit de arrastre");
        body.put("brand", "Vortex");
        body.put("condition", "AFTERMARKET");
        body.put("quantity", 1);
        body.put("unitPrice", unitPrice);
        if (code != null) body.put("productCode", code);
        return UUID.fromString(json(post("/api/v1/orders/" + v.orderId() + "/parts", v.shop().mechanic(), v.shop().ws(), body)
                .andExpect(status().isCreated())).get("partId").asString());
    }

    private Visit completed(Shop s, long labor, long partPrice) throws Exception {
        var v = visit(s, 1000);
        addJob(v, labor);
        if (partPrice > 0) addPart(v, partPrice, null);
        transition(v, s.mechanic(), "IN_PROGRESS", null).andExpect(status().isOk());
        transition(v, s.mechanic(), "COMPLETED", null).andExpect(status().isOk());
        return v;
    }

    private ResultActions deliver(Visit v, Session who) throws Exception {
        return post("/api/v1/orders/" + v.orderId() + "/delivery", who, v.shop().ws(), Map.of("version", version(v)));
    }

    @Test
    void fullFlowFromIntakeToSignedHistoryEntry() throws Exception {
        var s = shop();
        var v = completed(s, 50_000, 150_000);
        post("/api/v1/orders/" + v.orderId() + "/payments", s.desk(), s.ws(), Map.of("method", "CASH", "amount", 100_000)).andExpect(status().isCreated());
        post("/api/v1/orders/" + v.orderId() + "/payments", s.desk(), s.ws(), Map.of("method", "TRANSFER", "amount", 100_000)).andExpect(status().isCreated());

        var res = deliver(v, s.desk()).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(200_000))
                .andExpect(jsonPath("$.claimCode").exists()).andReturn().getResponse();
        assertThat(res.getHeader("Cache-Control")).contains("no-store");
        String code = json(deliver(v, s.desk()).andExpect(status().isConflict())).get("code").asString(); // second delivery refused
        assertThat(code).isEqualTo("INVALID_TRANSITION");

        // the claim code is stored only as a hash
        String claim = co.nitida.shared.Json.mapper().readTree(res.getContentAsString()).get("claimCode").asString();
        assertThat(claim).hasSize(8);
        assertThat(jdbc.sql("SELECT count(*) FROM motorcycles.claim_code WHERE code_hash = ?").param(claim).query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM motorcycles.claim_code WHERE motorcycle_id = ?").param(v.motorcycleId()).query(Long.class).single()).isEqualTo(1L);

        // delivery published OrderDelivered: history now has INITIAL_REGISTRATION + SERVICE, signed and valid
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(jdbc.sql("SELECT count(*) FROM history.history_entry WHERE motorcycle_id = ?").param(v.motorcycleId()).query(Long.class).single()).isEqualTo(2L));
        assertThat(history.verify(v.motorcycleId()).isValid()).isTrue();
        assertThat(history.lastMileage(v.motorcycleId()).orElseThrow().km()).isEqualTo(1000);
        var pub = jdbc.sql("SELECT public_content FROM history.history_entry WHERE motorcycle_id = ? AND seq = 2").param(v.motorcycleId()).query(String.class).single();
        assertThat(pub).doesNotContain("150000").doesNotContain("200000"); // prices stay private
    }

    @Test
    void openingAnOrderClosesTheIntakeAndASecondOrderForItIsRefused() throws Exception {
        var s = shop();
        var v = visit(s, 500);
        get("/api/v1/intakes", s.owner(), s.ws()).andExpect(jsonPath("$.length()").value(0));
        post("/api/v1/orders", s.mechanic(), s.ws(), Map.of("intakeId", v.intakeId(), "diagnosis", "Otro diagnóstico distinto"))
                .andExpect(status().isNotFound());
    }

    @Test
    void mileageRegressionIsRefusedUntilAnOdometerChangeResetsTheBaseline() throws Exception {
        var s = shop();
        var first = completed(s, 10_000, 0);
        post("/api/v1/orders/" + first.orderId() + "/payments", s.desk(), s.ws(), Map.of("method", "CASH", "amount", 10_000)).andExpect(status().isCreated());
        deliver(first, s.desk()).andExpect(status().isOk());
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(history.lastMileage(first.motorcycleId())).isPresent());

        // same motorcycle comes back reading 400 km: a regression against 1000
        var lowIntake = intake(s, jdbc.sql("SELECT current_plate FROM motorcycles.motorcycle WHERE id = ?").param(first.motorcycleId()).query(String.class).single(), 400);
        post("/api/v1/orders", s.mechanic(), s.ws(), Map.of("intakeId", lowIntake, "diagnosis", "Regreso con el tablero cambiado"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("MILEAGE_REGRESSION"));

        post("/api/v1/motorcycles/" + first.motorcycleId() + "/history/odometer-changes", s.owner(), s.ws(),
                Map.of("mileage", 400, "reason", "Se reemplazó el tablero por falla", "photoId", uploadPhoto(s.owner(), s.ws()))).andExpect(status().isCreated());
        post("/api/v1/orders", s.mechanic(), s.ws(), Map.of("intakeId", lowIntake, "diagnosis", "Regreso con el tablero cambiado"))
                .andExpect(status().isCreated());
    }

    @Test
    void eachRoleDoesOnlyWhatTheMatrixAllows() throws Exception {
        var s = shop();
        var v = visit(s, 700);
        // front desk cannot open or advance orders, nor edit them
        var intake2 = intake(s, plate(), 10);
        post("/api/v1/orders", s.desk(), s.ws(), Map.of("intakeId", intake2, "diagnosis", "Intento de recepción")).andExpect(status().isForbidden());
        transition(v, s.desk(), "IN_PROGRESS", null).andExpect(status().isForbidden());
        // mechanic cannot register payments, void them or deliver
        post("/api/v1/orders/" + v.orderId() + "/payments", s.mechanic(), s.ws(), Map.of("method", "CASH", "amount", 1)).andExpect(status().isForbidden());
        post("/api/v1/orders/" + v.orderId() + "/delivery", s.mechanic(), s.ws(), Map.of("version", 0)).andExpect(status().isForbidden());
        post("/api/v1/payments/" + UUID.randomUUID() + "/void", s.mechanic(), s.ws(), Map.of("reason", "no debería poder")).andExpect(status().isForbidden());
    }

    @Test
    void ordersOfAnotherWorkshopAreInvisible() throws Exception {
        var a = shop();
        var b = shop();
        var v = visit(a, 100);
        get("/api/v1/orders/" + v.orderId(), b.owner(), b.ws()).andExpect(status().isNotFound());
        call(HttpMethod.PATCH, "/api/v1/orders/" + v.orderId(), b.owner(), b.ws(), Map.of("version", 0, "diagnosis", "Intento de edición ajena"))
                .andExpect(status().isNotFound());
        post("/api/v1/orders/" + v.orderId() + "/transitions", b.owner(), b.ws(), Map.of("status", "IN_PROGRESS", "version", 0)).andExpect(status().isNotFound());
        get("/api/v1/orders", b.owner(), b.ws()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void mechanicsDoNotSeePaymentsInTheOrderDetail() throws Exception {
        var s = shop();
        var v = completed(s, 20_000, 0);
        post("/api/v1/orders/" + v.orderId() + "/payments", s.desk(), s.ws(), Map.of("method", "CASH", "amount", 5_000)).andExpect(status().isCreated());
        get("/api/v1/orders/" + v.orderId(), s.desk(), s.ws()).andExpect(jsonPath("$.payments.length()").value(1)).andExpect(jsonPath("$.paid").value(5_000));
        get("/api/v1/orders/" + v.orderId(), s.mechanic(), s.ws()).andExpect(jsonPath("$.payments.length()").value(0)).andExpect(jsonPath("$.paid").doesNotExist());
    }

    @Test
    void deliveryGuardsPaymentsVersionAndOverpayment() throws Exception {
        var s = shop();
        var v = completed(s, 10_000, 0);
        deliver(v, s.desk()).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("INCOMPLETE_PAYMENTS"));
        post("/api/v1/orders/" + v.orderId() + "/payments", s.desk(), s.ws(), Map.of("method", "CASH", "amount", 10_001))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("OVERPAYMENT"));
        var paymentId = json(post("/api/v1/orders/" + v.orderId() + "/payments", s.desk(), s.ws(), Map.of("method", "CASH", "amount", 10_000))
                .andExpect(status().isCreated())).get("paymentId").asString();
        post("/api/v1/orders/" + v.orderId() + "/delivery", s.desk(), s.ws(), Map.of("version", 999)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        // voiding is allowed before delivery and puts the order back to unpaid
        post("/api/v1/payments/" + paymentId + "/void", s.desk(), s.ws(), Map.of("reason", "Se digitó mal el valor")).andExpect(status().isNoContent());
        post("/api/v1/payments/" + paymentId + "/void", s.desk(), s.ws(), Map.of("reason", "Se digitó mal el valor")).andExpect(status().isConflict());
        deliver(v, s.desk()).andExpect(status().isUnprocessableContent());
    }

    @Test
    void storeCreditNeedsAnIdentifiedCustomer() throws Exception {
        var s = shop();
        var v = completed(s, 10_000, 0);
        post("/api/v1/orders/" + v.orderId() + "/payments", s.desk(), s.ws(), Map.of("method", "STORE_CREDIT", "amount", 10_000))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("CUSTOMER_REQUIRED"));
    }

    @Test
    void deliveredOrderCannotBeEditedOrVoided() throws Exception {
        var s = shop();
        var v = completed(s, 10_000, 0);
        var paymentId = json(post("/api/v1/orders/" + v.orderId() + "/payments", s.desk(), s.ws(), Map.of("method", "CASH", "amount", 10_000))).get("paymentId").asString();
        deliver(v, s.desk()).andExpect(status().isOk());
        call(HttpMethod.PATCH, "/api/v1/orders/" + v.orderId(), s.owner(), s.ws(), Map.of("version", version(v), "diagnosis", "Cambio posterior de diagnóstico"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_NOT_EDITABLE"));
        post("/api/v1/payments/" + paymentId + "/void", s.owner(), s.ws(), Map.of("reason", "Intento tras la entrega")).andExpect(status().isConflict());
        transition(v, s.owner(), "IN_PROGRESS", "Intento de reabrir una entregada").andExpect(status().isConflict());
    }

    @Test
    void zeroTotalIsOnlyDeliverableAsAWarrantyOfAnEarlierOrder() throws Exception {
        var s = shop();
        var plain = completed(s, 0, 0);
        deliver(plain, s.desk()).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("INVALID_WARRANTY"));

        var first = completed(s, 10_000, 0);
        post("/api/v1/orders/" + first.orderId() + "/payments", s.desk(), s.ws(), Map.of("method", "CASH", "amount", 10_000)).andExpect(status().isCreated());
        deliver(first, s.desk()).andExpect(status().isOk());

        String plate = jdbc.sql("SELECT current_plate FROM motorcycles.motorcycle WHERE id = ?").param(first.motorcycleId()).query(String.class).single();
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(history.lastMileage(first.motorcycleId())).isPresent());
        var intakeId = intake(s, plate, 1200);
        var order = json(post("/api/v1/orders", s.mechanic(), s.ws(),
                Map.of("intakeId", intakeId, "diagnosis", "Falla del trabajo anterior", "warrantyOfOrderId", first.orderId())).andExpect(status().isCreated()));
        var warranty = new Visit(s, intakeId, first.motorcycleId(), UUID.fromString(order.get("orderId").asString()));
        addJob(warranty, 0);
        transition(warranty, s.mechanic(), "IN_PROGRESS", null).andExpect(status().isOk());
        transition(warranty, s.mechanic(), "COMPLETED", null).andExpect(status().isOk());
        deliver(warranty, s.desk()).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void cancellingNeedsAReasonAndFreesTheSerials() throws Exception {
        var s = shop();
        var v = visit(s, 300);
        UUID part = addPart(v, 80_000, "4006381333931");
        var serial = "SN-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
        post("/api/v1/orders/" + v.orderId() + "/parts/" + part + "/serial", s.mechanic(), s.ws(), Map.of("serial", serial))
                .andExpect(jsonPath("$.accepted").value(true)).andExpect(jsonPath("$.evidenceLevel").value("UNIQUE_SERIAL"));
        assertThat(jdbc.sql("SELECT count(*) FROM catalog.serial WHERE normalized_serial = ? AND status = 'ACTIVE'").param(serial).query(Long.class).single()).isEqualTo(1L);

        transition(v, s.mechanic(), "CANCELLED", null).andExpect(status().isUnprocessableContent());
        transition(v, s.mechanic(), "CANCELLED", "El cliente retiró la moto sin reparar").andExpect(status().isOk());
        assertThat(jdbc.sql("SELECT count(*) FROM catalog.serial WHERE normalized_serial = ? AND status = 'ACTIVE'").param(serial).query(Long.class).single()).isZero();
    }

    @Test
    void aSerialAlreadyActiveOnAnotherMotorcycleIsRejectedQuietly() throws Exception {
        var s1 = shop();
        var s2 = shop();
        var serial = "SN-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
        var v1 = visit(s1, 100);
        post("/api/v1/orders/" + v1.orderId() + "/parts/" + addPart(v1, 1_000, null) + "/serial", s1.mechanic(), s1.ws(), Map.of("serial", serial))
                .andExpect(jsonPath("$.accepted").value(true));

        var v2 = visit(s2, 200);
        UUID part2 = addPart(v2, 1_000, "4006381333931");
        var res = post("/api/v1/orders/" + v2.orderId() + "/parts/" + part2 + "/serial", s2.mechanic(), s2.ws(), Map.of("serial", serial))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accepted").value(false)).andExpect(jsonPath("$.evidenceLevel").value("SCANNED"))
                .andReturn().getResponse().getContentAsString();
        // generic message: nothing about the other motorcycle or workshop (RN-94)
        assertThat(res).doesNotContain(v1.motorcycleId().toString()).doesNotContain(s1.ws().toString()).doesNotContain(serial);
    }

    @Test
    void authenticityNeedsPhotoAndMethodAndRaisesEvidenceToTheTop() throws Exception {
        var s = shop();
        var v = visit(s, 300);
        UUID part = addPart(v, 5_000, "4006381333931");
        String path = "/api/v1/orders/" + v.orderId() + "/parts/" + part + "/authenticity";
        var noPhoto = new HashMap<String, Object>();
        noPhoto.put("method", "Verificado en el sitio oficial del fabricante");
        post(path, s.mechanic(), s.ws(), noPhoto).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("PHOTO_REQUIRED"));
        post(path, s.mechanic(), s.ws(), Map.of("photoId", uploadPhoto(s.mechanic(), s.ws()), "method", "ok"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("INVALID_METHOD"));
        post(path, s.mechanic(), s.ws(), Map.of("photoId", uploadPhoto(s.mechanic(), s.ws()), "method", "Verificado en el sitio oficial del fabricante"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.evidenceLevel").value("AUTHENTICITY_VERIFIED"));
    }

    @Test
    void badProductCodeIsRejectedAndNewCodesBecomeCatalogProposals() throws Exception {
        var s = shop();
        var v = visit(s, 300);
        var bad = new HashMap<String, Object>();
        bad.put("name", "Filtro");
        bad.put("brand", "Marca");
        bad.put("condition", "OEM");
        bad.put("quantity", 1);
        bad.put("unitPrice", 100);
        bad.put("productCode", "4006381333932");
        post("/api/v1/orders/" + v.orderId() + "/parts", s.mechanic(), s.ws(), bad).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_PRODUCT_CODE"));
        addPart(v, 100, "4006381333931");
        get("/api/v1/catalog/products/4006381333931", s.mechanic(), s.ws()).andExpect(jsonPath("$.found").value(true));
    }

    @Test
    void photosAndJobsAreValidated() throws Exception {
        var s = shop();
        var v = visit(s, 300);
        post("/api/v1/orders/" + v.orderId() + "/photos", s.mechanic(), s.ws(), Map.of("fileId", uploadPhoto(s.mechanic(), s.ws()), "moment", "BEFORE"))
                .andExpect(status().isNoContent());
        post("/api/v1/orders/" + v.orderId() + "/photos", s.mechanic(), s.ws(), Map.of("fileId", UUID.randomUUID(), "moment", "AFTER")).andExpect(status().isNotFound());
        call(HttpMethod.PATCH, "/api/v1/orders/" + v.orderId(), s.mechanic(), s.ws(),
                Map.of("version", version(v), "jobs", List.of(Map.of("type", "NOPE", "description", "algo")))).andExpect(status().isBadRequest());
        transition(v, s.mechanic(), "IN_PROGRESS", null).andExpect(status().isOk());
        transition(v, s.mechanic(), "COMPLETED", null).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("JOBS_REQUIRED"));
    }

    @Test
    void invalidTransitionsAreConflicts() throws Exception {
        var s = shop();
        var v = visit(s, 300);
        transition(v, s.mechanic(), "COMPLETED", null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
        transition(v, s.mechanic(), "DELIVERED", null).andExpect(status().isConflict());
        post("/api/v1/orders/" + v.orderId() + "/transitions", s.mechanic(), s.ws(), Map.of("status", "IN_PROGRESS", "version", 99))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }
}
