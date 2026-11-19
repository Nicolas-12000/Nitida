package co.nitida.workshops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.nitida.identity.application.Mailbox;
import co.nitida.support.IntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class WorkshopsIntegrationTest extends IntegrationTest {

    @Test
    void registeringAWorkshopMakesTheUserOwnerAndStartsPending() throws Exception {
        var owner = registerAndLogin();
        UUID ws = createWorkshop(owner);
        get("/api/v1/workshops/" + ws, owner, ws).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"));
        get("/api/v1/workshops/mine", owner, null).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roles[0]").value("WORKSHOP_OWNER"));
        // a signing key was provisioned
        assertThat(jdbc.sql("SELECT count(*) FROM workshops.signing_key WHERE workshop_id = ?").param(ws).query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    void invalidWorkshopDataIsRejectedWithSpecificCodes() throws Exception {
        var owner = registerAndLogin();
        post("/api/v1/workshops", owner, null, Map.of("name", "T", "documentNumber", "900123456", "address", "Calle 1 # 2-3",
                "municipality", "Pasto", "phone", "3101234567")).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_NAME"));
        post("/api/v1/workshops", owner, null, Map.of("name", "Taller", "documentNumber", "abc", "address", "Calle 1 # 2-3",
                "municipality", "Pasto", "phone", "3101234567")).andExpect(jsonPath("$.code").value("INVALID_DOCUMENT"));
        post("/api/v1/workshops", owner, null, Map.of("name", "Taller", "documentNumber", "900123456", "address", "Calle 1 # 2-3",
                "municipality", "Pasto", "phone", "123")).andExpect(jsonPath("$.code").value("INVALID_PHONE"));
    }

    @Test
    void aWorkshopOfAnotherUserLooksNonExistent() throws Exception {
        var a = registerAndLogin();
        var b = registerAndLogin();
        UUID wsA = createWorkshop(a);
        get("/api/v1/workshops/" + wsA, b, wsA).andExpect(status().isNotFound());
        get("/api/v1/workshops/" + wsA, b, UUID.randomUUID()).andExpect(status().isNotFound());
        get("/api/v1/workshops/" + wsA, b, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WORKSHOP_REQUIRED"));
    }

    @Test
    void invitationFlowGivesTheRoleAndIsSingleUse() throws Exception {
        var owner = registerAndLogin();
        UUID ws = createWorkshop(owner);
        var mechanic = registerAndLogin();
        var inv = post("/api/v1/workshops/" + ws + "/invitations", owner, ws, Map.of("email", mechanic.email(), "role", "MECHANIC"))
                .andExpect(status().isCreated());
        String token = Mailbox.tokenOf(mailbox.last().text());
        assertThat(inv.andReturn().getResponse().getContentAsString()).doesNotContain(token);
        // token is never stored in clear
        assertThat(jdbc.sql("SELECT count(*) FROM identity.invitation WHERE token_hash = ?").param(token).query(Long.class).single()).isZero();

        post("/api/v1/invitations/" + token + "/accept", mechanic, null, null).andExpect(status().isOk());
        get("/api/v1/workshops/" + ws, mechanic, ws).andExpect(status().isOk());
        post("/api/v1/invitations/" + token + "/accept", mechanic, null, null).andExpect(status().isNotFound());
    }

    @Test
    void invitationOnlyWorksForTheInvitedEmail() throws Exception {
        var owner = registerAndLogin();
        UUID ws = createWorkshop(owner);
        var invited = registerAndLogin();
        var other = registerAndLogin();
        post("/api/v1/workshops/" + ws + "/invitations", owner, ws, Map.of("email", invited.email(), "role", "FRONT_DESK")).andReturn();
        String token = Mailbox.tokenOf(mailbox.last().text());
        post("/api/v1/invitations/" + token + "/accept", other, null, null).andExpect(status().isNotFound());
    }

    @Test
    void expiredInvitationIsNotFound() throws Exception {
        var owner = registerAndLogin();
        UUID ws = createWorkshop(owner);
        var invited = registerAndLogin();
        post("/api/v1/workshops/" + ws + "/invitations", owner, ws, Map.of("email", invited.email(), "role", "MECHANIC")).andReturn();
        String token = Mailbox.tokenOf(mailbox.last().text());
        jdbc.sql("UPDATE identity.invitation SET expires_at = ? WHERE workshop_id = ?")
                .params(java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)), ws).update();
        post("/api/v1/invitations/" + token + "/accept", invited, null, null).andExpect(status().isNotFound());
    }

    @Test
    void onlyTheOwnerInvitesAndManagesMembers() throws Exception {
        var owner = registerAndLogin();
        UUID ws = createWorkshop(owner);
        var mechanic = addMember(owner, ws, "MECHANIC");
        post("/api/v1/workshops/" + ws + "/invitations", mechanic, ws, Map.of("email", uniqueEmail(), "role", "MECHANIC"))
                .andExpect(status().isForbidden());
        get("/api/v1/workshops/" + ws + "/members", mechanic, ws).andExpect(status().isForbidden());
        get("/api/v1/workshops/" + ws + "/members", owner, ws).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void removedMemberLosesAccessAndTheLastOwnerCannotBeRemoved() throws Exception {
        var owner = registerAndLogin();
        UUID ws = createWorkshop(owner);
        var mechanic = addMember(owner, ws, "MECHANIC");
        call(HttpMethod.DELETE, "/api/v1/workshops/" + ws + "/members/" + mechanic.userId(), owner, ws, null)
                .andExpect(status().isNoContent());
        get("/api/v1/workshops/" + ws, mechanic, ws).andExpect(status().isNotFound());
        call(HttpMethod.DELETE, "/api/v1/workshops/" + ws + "/members/" + owner.userId(), owner, ws, null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_OWNER"));
    }

    @Test
    void adminVerifiesAndSuspendsWithEvidenceAndAudit() throws Exception {
        var owner = registerAndLogin();
        UUID ws = createWorkshop(owner);
        var admin = registerAdmin();
        post("/api/v1/admin/workshops/" + ws + "/status", admin, null, Map.of("status", "VERIFIED", "evidence", "corta"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("EVIDENCE_REQUIRED"));
        post("/api/v1/admin/workshops/" + ws + "/status", admin, null,
                Map.of("status", "VERIFIED", "evidence", "Visita presencial el 18 de noviembre")).andExpect(status().isOk());
        post("/api/v1/admin/workshops/" + ws + "/status", admin, null,
                Map.of("status", "VERIFIED", "evidence", "Visita presencial el 18 de noviembre"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
        post("/api/v1/admin/workshops/" + ws + "/status", admin, null,
                Map.of("status", "SUSPENDED", "evidence", "Denuncia de clientes verificada")).andExpect(status().isOk());
        assertThat(jdbc.sql("SELECT count(*) FROM audit.audit_event WHERE workshop_id = ? AND action = 'WORKSHOP_STATUS_CHANGED'")
                .param(ws).query(Long.class).single()).isEqualTo(2L);
    }

    @Test
    void nonAdminsCannotTouchTheAdminArea() throws Exception {
        var owner = registerAndLogin();
        UUID ws = createWorkshop(owner);
        post("/api/v1/admin/workshops/" + ws + "/status", owner, ws, Map.of("status", "VERIFIED", "evidence", "Visita presencial hoy"))
                .andExpect(status().isForbidden());
        get("/api/v1/admin/workshops", owner, null).andExpect(status().isForbidden());
        get("/api/v1/admin/workshops", registerAdmin(), null).andExpect(status().isOk());
    }

    @Test
    void settingsAreValidatedAndUseOptimisticLocking() throws Exception {
        var owner = registerAndLogin();
        UUID ws = createWorkshop(owner);
        long version = json(get("/api/v1/workshops/" + ws, owner, ws)).has("id") ? 0 : 0;
        String path = "/api/v1/workshops/" + ws + "/settings";
        call(HttpMethod.PATCH, path, owner, ws, Map.of("intakeAlertHours", 0, "cashTolerance", 0, "stalledDays", 7, "version", version))
                .andExpect(status().isUnprocessableContent());
        call(HttpMethod.PATCH, path, owner, ws, Map.of("intakeAlertHours", 169, "cashTolerance", 0, "stalledDays", 7, "version", version))
                .andExpect(status().isUnprocessableContent());
        call(HttpMethod.PATCH, path, owner, ws, Map.of("intakeAlertHours", 168, "cashTolerance", 50_001, "stalledDays", 7, "version", version))
                .andExpect(status().isUnprocessableContent());
        call(HttpMethod.PATCH, path, owner, ws, Map.of("intakeAlertHours", 1, "cashTolerance", 50_000, "stalledDays", 7, "version", version))
                .andExpect(status().isOk()).andExpect(jsonPath("$.intakeAlertHours").value(1));
        call(HttpMethod.PATCH, path, owner, ws, Map.of("intakeAlertHours", 2, "cashTolerance", 0, "stalledDays", 7, "version", version))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    void publicKeysAreExposedWithoutAuthentication() throws Exception {
        var owner = registerAndLogin();
        createWorkshop(owner);
        get("/api/v1/public/keys", null, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("OKP")).andExpect(jsonPath("$.keys[0].crv").value("Ed25519"));
    }
}
