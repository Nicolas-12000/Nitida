package co.nitida.motorcycles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.nitida.motorcycles.MotorcyclesApi.MotorcycleDetails;
import co.nitida.shared.ApiException;
import co.nitida.support.IntegrationTest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

class MotorcyclesIntegrationTest extends IntegrationTest {
    private static final MotorcycleDetails DETAILS = new MotorcycleDetails("Yamaha", "FZ 2.0", 2022, 150);
    private static final String VIN = "9C2JC4010NR000001";

    @Autowired MotorcyclesApi api;

    @Test
    void newMotorcycleIsCreatedOnceAndFoundAfterwards() {
        UUID ws = UUID.randomUUID();
        var first = api.findOrRegister(ws, "qwe-12-r", null, DETAILS, 1200);
        assertThat(first.created()).isTrue();
        assertThat(first.motorcycle().plate()).isEqualTo("QWE12R");
        var again = api.findOrRegister(UUID.randomUUID(), "QWE12R", null, DETAILS, 5000);
        assertThat(again.created()).isFalse();
        assertThat(again.motorcycle().id()).isEqualTo(first.motorcycle().id());
    }

    @Test
    void lateVinIsAssociatedToThePlateOnlyMotorcycleWithoutDuplicating() {
        String vin = "9C2JC4010NR" + String.format("%06d", Math.abs(UUID.randomUUID().hashCode()) % 1_000_000);
        var first = api.findOrRegister(UUID.randomUUID(), "VIN11A", null, DETAILS, 10);
        var second = api.findOrRegister(UUID.randomUUID(), "VIN11A", vin, null, 20);
        assertThat(second.created()).isFalse();
        assertThat(second.motorcycle().id()).isEqualTo(first.motorcycle().id());
        assertThat(api.lookup(null, vin)).get().extracting(MotorcyclesApi.MotorcycleInfo::id).isEqualTo(first.motorcycle().id());
        assertThat(api.findOrRegister(UUID.randomUUID(), null, vin, null, 30).created()).isFalse();
    }

    @Test
    void samePlateWithAnotherVinIsAConflict() {
        api.findOrRegister(UUID.randomUUID(), "MIS11B", "9C2JC4010NR111111", DETAILS, 10);
        assertThatThrownBy(() -> api.findOrRegister(UUID.randomUUID(), "MIS11B", "9C2JC4010NR222222", null, 10))
                .isInstanceOf(ApiException.class).extracting(e -> ((ApiException) e).code()).isEqualTo("PLATE_VIN_MISMATCH");
    }

    @Test
    void invalidInputIsRejectedEvenWhenTheMotorcycleExists() {
        assertThatThrownBy(() -> api.findOrRegister(UUID.randomUUID(), "bad", null, DETAILS, 10)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> api.findOrRegister(UUID.randomUUID(), null, null, DETAILS, 10)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> api.findOrRegister(UUID.randomUUID(), "NEW11C", null, DETAILS, 1_000_000)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> api.findOrRegister(UUID.randomUUID(), "NEW12C", null, null, 10))
                .extracting(e -> ((ApiException) e).code()).isEqualTo("DETAILS_REQUIRED");
    }

    private Map<String, Object> customerBody(String name, boolean consent, String policy) {
        var m = new HashMap<String, Object>();
        m.put("fullName", name);
        m.put("phone", "3101234567");
        m.put("consentAccepted", consent);
        m.put("policyVersion", policy);
        m.put("consentChannel", "IN_PERSON");
        return m;
    }

    @Test
    void customerRequiresConsentOfTheCurrentPolicy() throws Exception {
        var owner = registerAndLogin();
        UUID ws = createWorkshop(owner);
        post("/api/v1/customers", owner, ws, customerBody("Ana Gómez", false, "2026-1"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
        post("/api/v1/customers", owner, ws, customerBody("Ana Gómez", true, "1999-1"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("STALE_POLICY"));
        assertThat(jdbc.sql("SELECT count(*) FROM motorcycles.customer WHERE workshop_id = ?").param(ws).query(Long.class).single()).isZero();
        post("/api/v1/customers", owner, ws, customerBody("Ana Gómez", true, "2026-1")).andExpect(status().isCreated());
        assertThat(jdbc.sql("SELECT count(*) FROM motorcycles.data_consent WHERE workshop_id = ?").param(ws).query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    void mechanicsCannotCreateCustomersButCanSearch() throws Exception {
        var owner = registerAndLogin();
        UUID ws = createWorkshop(owner);
        var mechanic = addMember(owner, ws, "MECHANIC");
        post("/api/v1/customers", mechanic, ws, customerBody("Ana Gómez", true, "2026-1")).andExpect(status().isForbidden());
        post("/api/v1/customers", owner, ws, customerBody("Ana Gómez", true, "2026-1")).andExpect(status().isCreated());
        get("/api/v1/customers?q=gómez", mechanic, ws).andExpect(status().isOk()).andExpect(jsonPath("$[0].fullName").value("Ana Gómez"));
        get("/api/v1/customers?q=a", mechanic, ws).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void customersNeverLeakAcrossWorkshops() throws Exception {
        var a = registerAndLogin();
        var b = registerAndLogin();
        UUID wsA = createWorkshop(a);
        UUID wsB = createWorkshop(b);
        var created = post("/api/v1/customers", a, wsA, customerBody("Cliente Secreto", true, "2026-1")).andExpect(status().isCreated());
        String id = json(created).get("customerId").asString();
        get("/api/v1/customers?q=secreto", b, wsB).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        call(HttpMethod.PATCH, "/api/v1/customers/" + id, b, wsB, Map.of("fullName", "Hackeado", "phone", "3101234567"))
                .andExpect(status().isNotFound());
        post("/api/v1/customers/" + id + "/erasure", b, wsB, null).andExpect(status().isNotFound());
    }

    @Test
    void erasureAnonymizesPersonalDataAndHidesTheCustomer() throws Exception {
        var owner = registerAndLogin();
        UUID ws = createWorkshop(owner);
        String id = json(post("/api/v1/customers", owner, ws, customerBody("Luis Pérez", true, "2026-1"))).get("customerId").asString();
        post("/api/v1/customers/" + id + "/erasure", owner, ws, null).andExpect(status().isNoContent());
        post("/api/v1/customers/" + id + "/erasure", owner, ws, null).andExpect(status().isNoContent()); // idempotent
        var row = jdbc.sql("SELECT full_name, phone, national_id, email FROM motorcycles.customer WHERE id = ?")
                .param(UUID.fromString(id)).query().singleRow();
        assertThat(row.get("full_name")).isEqualTo("Cliente eliminado");
        assertThat(row.get("national_id")).isNull();
        get("/api/v1/customers?q=pérez", owner, ws).andExpect(jsonPath("$.length()").value(0));
        call(HttpMethod.PATCH, "/api/v1/customers/" + id, owner, ws, Map.of("fullName", "Otro", "phone", "3101234567"))
                .andExpect(status().isNotFound());
    }
}
