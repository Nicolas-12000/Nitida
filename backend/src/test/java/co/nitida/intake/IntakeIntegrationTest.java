package co.nitida.intake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.nitida.support.IntegrationTest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class IntakeIntegrationTest extends IntegrationTest {

    private Map<String, Object> body(String plate, int mileage, UUID odo, UUID moto) {
        var m = new HashMap<String, Object>();
        m.put("plate", plate);
        m.put("brand", "Yamaha");
        m.put("line", "FZ 2.0");
        m.put("modelYear", 2022);
        m.put("displacementCc", 150);
        m.put("mileage", mileage);
        m.put("odometerPhotoId", odo);
        m.put("motorcyclePhotoId", moto);
        return m;
    }

    private ResultActions register(Session s, UUID ws, String plate, int km) throws Exception {
        return post("/api/v1/intakes", s, ws, body(plate, km, uploadPhoto(s, ws), uploadPhoto(s, ws)));
    }

    @Test
    void receivingANewMotorcycleCreatesItAndAnOpenIntake() throws Exception {
        var owner = registerAndLogin();
        UUID ws = createWorkshop(owner);
        register(owner, ws, "INA11A", 12_000).andExpect(status().isCreated()).andExpect(jsonPath("$.newMotorcycle").value(true));
        get("/api/v1/intakes", owner, ws).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].plate").value("INA11A")).andExpect(jsonPath("$[0].mileage").value(12000));
    }

    @Test
    void anotherWorkshopReceivingTheSameMotorcycleReusesIt() throws Exception {
        var a = registerAndLogin();
        var b = registerAndLogin();
        UUID wsA = createWorkshop(a);
        UUID wsB = createWorkshop(b);
        String first = json(register(a, wsA, "INB11B", 100)).get("motorcycleId").asString();
        register(b, wsB, "inb-11-b", 200).andExpect(jsonPath("$.newMotorcycle").value(false)).andExpect(jsonPath("$.motorcycleId").value(first));
    }

    @Test
    void photosAreRequiredAndMustBelongToTheWorkshop() throws Exception {
        var a = registerAndLogin();
        var b = registerAndLogin();
        UUID wsA = createWorkshop(a);
        UUID wsB = createWorkshop(b);
        post("/api/v1/intakes", a, wsA, body("INC11C", 10, null, null)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PHOTO_REQUIRED"));
        UUID foreign = uploadPhoto(b, wsB);
        post("/api/v1/intakes", a, wsA, body("INC11C", 10, foreign, uploadPhoto(a, wsA))).andExpect(status().isNotFound());
    }

    @Test
    void mileageBoundariesAndInvalidPlates() throws Exception {
        var owner = registerAndLogin();
        UUID ws = createWorkshop(owner);
        register(owner, ws, "IND11D", 0).andExpect(status().isCreated());
        register(owner, ws, "IND12D", 999_999).andExpect(status().isCreated());
        register(owner, ws, "IND13D", 1_000_000).andExpect(jsonPath("$.code").value("INVALID_MILEAGE"));
        register(owner, ws, "IND14D", -1).andExpect(jsonPath("$.code").value("INVALID_MILEAGE"));
        register(owner, ws, "AB123D", 10).andExpect(jsonPath("$.code").value("INVALID_PLATE"));
    }

    @Test
    void aMotorcycleCannotHaveTwoOpenIntakesInTheSameWorkshop() throws Exception {
        var owner = registerAndLogin();
        UUID ws = createWorkshop(owner);
        register(owner, ws, "INE11E", 10).andExpect(status().isCreated());
        register(owner, ws, "INE11E", 11).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INTAKE_ALREADY_OPEN"));
    }

    @Test
    void discardClosesTheIntakeOnceAndRequiresAValidReason() throws Exception {
        var owner = registerAndLogin();
        UUID ws = createWorkshop(owner);
        String id = json(register(owner, ws, "INF11F", 10)).get("intakeId").asString();
        post("/api/v1/intakes/" + id + "/discard", owner, ws, Map.of("reason", "NOPE")).andExpect(status().isBadRequest());
        post("/api/v1/intakes/" + id + "/discard", owner, ws, Map.of("reason", "CONSULTATION_ONLY")).andExpect(status().isNoContent());
        post("/api/v1/intakes/" + id + "/discard", owner, ws, Map.of("reason", "ENTRY_ERROR")).andExpect(status().isConflict());
        get("/api/v1/intakes", owner, ws).andExpect(jsonPath("$.length()").value(0));
        // the motorcycle can be received again once the previous intake is closed
        register(owner, ws, "INF11F", 12).andExpect(status().isCreated());
    }

    @Test
    void suspendedWorkshopCannotReceiveMotorcycles() throws Exception {
        var owner = registerAndLogin();
        UUID ws = createWorkshop(owner);
        var photoA = uploadPhoto(owner, ws);
        var photoB = uploadPhoto(owner, ws);
        post("/api/v1/admin/workshops/" + ws + "/status", registerAdmin(), null,
                Map.of("status", "SUSPENDED", "evidence", "Suspendido por incumplimiento")).andExpect(status().isOk());
        post("/api/v1/intakes", owner, ws, body("ING11G", 10, photoA, photoB)).andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("WORKSHOP_SUSPENDED"));
    }

    @Test
    void intakesOfOtherWorkshopsAreInvisibleAndCannotBeDiscarded() throws Exception {
        var a = registerAndLogin();
        var b = registerAndLogin();
        UUID wsA = createWorkshop(a);
        UUID wsB = createWorkshop(b);
        String id = json(register(a, wsA, "INH11H", 10)).get("intakeId").asString();
        get("/api/v1/intakes", b, wsB).andExpect(jsonPath("$.length()").value(0));
        post("/api/v1/intakes/" + id + "/discard", b, wsB, Map.of("reason", "ENTRY_ERROR")).andExpect(status().isNotFound());
        assertThat(id).isNotBlank();
    }
}
