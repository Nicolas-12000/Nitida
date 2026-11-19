package co.nitida.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.nitida.shared.events.OrderDelivered;
import co.nitida.support.IntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpMethod;
import org.springframework.transaction.support.TransactionTemplate;

class HistoryIntegrationTest extends IntegrationTest {
    @Autowired HistoryApi history;
    @Autowired ApplicationEventPublisher publisher;
    @Autowired TransactionTemplate tx;

    record Fixture(Session owner, UUID workshop, UUID motorcycle) {}

    private static int plateSeq = 0;

    /** Receives a new motorcycle (which starts its chain) and waits for the INITIAL_REGISTRATION entry. */
    private Fixture fixture() throws Exception {
        var owner = registerAndLogin();
        UUID ws = createWorkshop(owner);
        String plate = "HI" + (char) ('A' + (plateSeq / 100) % 26) + String.format("%02d", plateSeq++ % 100) + "Z";
        var body = new HashMap<String, Object>();
        body.put("plate", plate);
        body.put("brand", "Yamaha");
        body.put("line", "FZ 2.0");
        body.put("mileage", 1000);
        body.put("odometerPhotoId", uploadPhoto(owner, ws));
        body.put("motorcyclePhotoId", uploadPhoto(owner, ws));
        UUID moto = UUID.fromString(json(post("/api/v1/intakes", owner, ws, body).andExpect(status().isCreated())).get("motorcycleId").asString());
        awaitEntries(moto, 1);
        return new Fixture(owner, ws, moto);
    }

    private void awaitEntries(UUID moto, long count) {
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(jdbc.sql("SELECT count(*) FROM history.history_entry WHERE motorcycle_id = ?").param(moto).query(Long.class).single()).isEqualTo(count));
    }

    private OrderDelivered delivered(Fixture f, UUID orderId, int mileage, UUID mechanic) {
        var part = new OrderDelivered.Part("Kit de arrastre", "KT-1", "Vortex", "AFTERMARKET", 1, 150_000, "4006381333931",
                "SER-12345", "UNIQUE_SERIAL", null, false, false);
        return new OrderDelivered(orderId, f.workshop(), f.motorcycle(), mileage, "Ruido en la transmisión al acelerar",
                List.of(new OrderDelivered.Job("DRIVE_CHAIN_KIT", "Cambio de kit completo")), List.of(part), 50_000, 200_000,
                List.of("abc123"), mechanic, 30, 1000, Instant.now());
    }

    private void publish(Object event) {
        tx.executeWithoutResult(s -> publisher.publishEvent(event));
    }

    @Test
    void newMotorcycleStartsItsChainWithASignedDeclaredInitialEntry() throws Exception {
        var f = fixture();
        var row = jdbc.sql("SELECT seq, type FROM history.history_entry WHERE motorcycle_id = ?").param(f.motorcycle()).query().singleRow();
        assertThat(row.get("type")).isEqualTo("INITIAL_REGISTRATION");
        assertThat(((Number) row.get("seq")).longValue()).isEqualTo(1);
        assertThat(history.verify(f.motorcycle()).isValid()).isTrue();
        assertThat(history.lastMileage(f.motorcycle()).orElseThrow().km()).isEqualTo(1000);
    }

    @Test
    void deliveredOrderBecomesAServiceEntryWithoutPricesOrClearSerialsInThePublicContent() throws Exception {
        var f = fixture();
        publish(delivered(f, UUID.randomUUID(), 1500, UUID.randomUUID()));
        awaitEntries(f.motorcycle(), 2);
        var row = jdbc.sql("SELECT public_content, private_content FROM history.history_entry WHERE motorcycle_id = ? AND seq = 2")
                .param(f.motorcycle()).query().singleRow();
        String pub = (String) row.get("public_content");
        String priv = (String) row.get("private_content");
        assertThat(pub).doesNotContain("150000").doesNotContain("200000").doesNotContain("SER-12345").contains("serialHash")
                .contains("\"workshopVerified\":false").contains("UNIQUE_SERIAL");
        assertThat(priv).contains("SER-12345").contains("150000").contains("salt");
        assertThat(history.verify(f.motorcycle()).isValid()).isTrue();
        assertThat(history.lastMileage(f.motorcycle()).orElseThrow().km()).isEqualTo(1500);
    }

    @Test
    void redeliveringTheSameEventDoesNotCreateASecondEntry() throws Exception {
        var f = fixture();
        var event = delivered(f, UUID.randomUUID(), 1500, UUID.randomUUID());
        publish(event);
        awaitEntries(f.motorcycle(), 2);
        publish(event);
        Thread.sleep(1500);
        assertThat(jdbc.sql("SELECT count(*) FROM history.history_entry WHERE motorcycle_id = ?").param(f.motorcycle()).query(Long.class).single()).isEqualTo(2L);
    }

    @Test
    void concurrentDeliveriesGetConsecutiveSequenceNumbersAndAValidChain() throws Exception {
        var f = fixture();
        var pool = Executors.newFixedThreadPool(4);
        var start = new CountDownLatch(1);
        for (int i = 0; i < 4; i++) {
            var event = delivered(f, UUID.randomUUID(), 1500 + i, UUID.randomUUID());
            pool.submit(() -> { start.await(); publish(event); return null; });
        }
        start.countDown();
        awaitEntries(f.motorcycle(), 5);
        pool.shutdown();
        var seqs = jdbc.sql("SELECT seq FROM history.history_entry WHERE motorcycle_id = ? ORDER BY seq").param(f.motorcycle()).query(Long.class).list();
        assertThat(seqs).containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(history.verify(f.motorcycle()).isValid()).isTrue();
    }

    @Test
    void tamperingWithStoredContentOrDeletingEntriesIsDetected() throws Exception {
        var f = fixture();
        publish(delivered(f, UUID.randomUUID(), 1500, UUID.randomUUID()));
        awaitEntries(f.motorcycle(), 2);
        jdbc.sql("UPDATE history.history_entry SET public_content = REPLACE(public_content, '1500', '1200') WHERE motorcycle_id = ? AND seq = 2")
                .param(f.motorcycle()).update();
        var r = history.verify(f.motorcycle());
        assertThat(r.status()).isEqualTo(VerificationResult.Status.INVALID);
        assertThat(r.seq()).isEqualTo(2);

        var g = fixture();
        publish(delivered(g, UUID.randomUUID(), 1500, UUID.randomUUID()));
        awaitEntries(g.motorcycle(), 2);
        jdbc.sql("DELETE FROM history.history_entry WHERE motorcycle_id = ? AND seq = 2").param(g.motorcycle()).update();
        assertThat(history.verify(g.motorcycle()).status()).isEqualTo(VerificationResult.Status.INVALID);
    }

    @Test
    void unknownSigningKeyMakesTheChainUnverifiable() throws Exception {
        var f = fixture();
        jdbc.sql("UPDATE history.history_entry SET key_id = ? WHERE motorcycle_id = ?").params(UUID.randomUUID(), f.motorcycle()).update();
        assertThat(history.verify(f.motorcycle()).status()).isEqualTo(VerificationResult.Status.UNVERIFIABLE);
    }

    @Test
    void publicLinkShowsTheVerifiedHistoryWithMaskedPlateAndNoPrivateData() throws Exception {
        var f = fixture();
        publish(delivered(f, UUID.randomUUID(), 1500, UUID.randomUUID()));
        awaitEntries(f.motorcycle(), 2);
        String token = json(post("/api/v1/motorcycles/" + f.motorcycle() + "/links", f.owner(), f.workshop(), Map.of("days", 7))
                .andExpect(status().isCreated())).get("token").asString();
        assertThat(jdbc.sql("SELECT count(*) FROM history.public_link WHERE token_hash = ?").param(token).query(Long.class).single()).isZero();

        var res = get("/api/v1/public/v/" + token, null, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.verification.status").value("VALID"))
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.evidence.partsTotal").value(1))
                .andReturn().getResponse();
        String body = res.getContentAsString();
        assertThat(res.getHeader("X-Robots-Tag")).contains("noindex");
        assertThat(body).doesNotContain("150000").doesNotContain("SER-12345").doesNotContain("privateContent").doesNotContain("salt").doesNotContain("Persona de prueba");
        assertThat(body).containsPattern("\"plate\":\"HI.\\*\\*Z\"");
    }

    @Test
    void publicViewShowsInvalidWhenTheChainWasTamperedWith() throws Exception {
        var f = fixture();
        String token = json(post("/api/v1/motorcycles/" + f.motorcycle() + "/links", f.owner(), f.workshop(), Map.of())).get("token").asString();
        jdbc.sql("UPDATE history.history_entry SET public_content = REPLACE(public_content, '1000', '10') WHERE motorcycle_id = ?").param(f.motorcycle()).update();
        get("/api/v1/public/v/" + token, null, null).andExpect(status().isOk()).andExpect(jsonPath("$.verification.status").value("INVALID"));
    }

    @Test
    void unknownExpiredAndRevokedLinksAreIndistinguishable() throws Exception {
        var f = fixture();
        var created = json(post("/api/v1/motorcycles/" + f.motorcycle() + "/links", f.owner(), f.workshop(), Map.of("days", 1)));
        String token = created.get("token").asString();
        String unknown = get("/api/v1/public/v/AAAAAAAAAAAAAAAAAAAAAA", null, null).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();

        call(HttpMethod.DELETE, "/api/v1/links/" + created.get("linkId").asString(), f.owner(), f.workshop(), null).andExpect(status().isNoContent());
        String revoked = get("/api/v1/public/v/" + token, null, null).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();

        String token2 = json(post("/api/v1/motorcycles/" + f.motorcycle() + "/links", f.owner(), f.workshop(), Map.of("days", 1))).get("token").asString();
        jdbc.sql("UPDATE history.public_link SET expires_at = ? WHERE token_hash = ?")
                .params(java.sql.Timestamp.from(Instant.now().minusMillis(1)), co.nitida.shared.crypto.SecureTokens.hash(token2)).update();
        String expired = get("/api/v1/public/v/" + token2, null, null).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();

        var mapper = co.nitida.shared.Json.mapper();
        assertThat(mapper.readTree(unknown).get("code")).isEqualTo(mapper.readTree(revoked).get("code")).isEqualTo(mapper.readTree(expired).get("code"));
        assertThat(mapper.readTree(unknown).get("title")).isEqualTo(mapper.readTree(expired).get("title"));
    }

    @Test
    void linkExpirationIsBoundedAndWorkshopMustHaveAttendedTheMotorcycle() throws Exception {
        var f = fixture();
        post("/api/v1/motorcycles/" + f.motorcycle() + "/links", f.owner(), f.workshop(), Map.of("days", 0)).andExpect(status().isUnprocessableContent());
        post("/api/v1/motorcycles/" + f.motorcycle() + "/links", f.owner(), f.workshop(), Map.of("days", 366)).andExpect(status().isUnprocessableContent());
        post("/api/v1/motorcycles/" + f.motorcycle() + "/links", f.owner(), f.workshop(), Map.of("days", 365)).andExpect(status().isCreated());
        var stranger = registerAndLogin();
        UUID otherWs = createWorkshop(stranger);
        post("/api/v1/motorcycles/" + f.motorcycle() + "/links", stranger, otherWs, Map.of()).andExpect(status().isNotFound());
        get("/api/v1/motorcycles/" + f.motorcycle() + "/history", stranger, otherWs).andExpect(status().isNotFound());
        get("/api/v1/motorcycles/" + f.motorcycle() + "/history", f.owner(), f.workshop()).andExpect(status().isOk())
                .andExpect(jsonPath("$.verification.status").value("VALID"));
    }

    @Test
    void amendmentAddsAnEntryAndOnlyTheOriginalWorkshopMayWriteIt() throws Exception {
        var f = fixture();
        var mechanic = addMember(f.owner(), f.workshop(), "MECHANIC");
        publish(delivered(f, UUID.randomUUID(), 1500, mechanic.userId()));
        awaitEntries(f.motorcycle(), 2);
        String path = "/api/v1/motorcycles/" + f.motorcycle() + "/history/amendments";
        post(path, f.owner(), f.workshop(), Map.of("amendsSeq", 2, "reason", "Se digitó mal el diagnóstico", "diagnosis", "Ruido en la cadena al acelerar"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.seq").value(3));
        post(path, f.owner(), f.workshop(), Map.of("amendsSeq", 3, "reason", "Corrección de la corrección", "mileage", 1400))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("CANNOT_AMEND_AMENDMENT"));
        post(path, f.owner(), f.workshop(), Map.of("amendsSeq", 2, "reason", "corta")).andExpect(status().isUnprocessableContent());
        post(path, f.owner(), f.workshop(), Map.of("amendsSeq", 2, "reason", "Sin ningún campo a corregir"))
                .andExpect(jsonPath("$.code").value("NOTHING_TO_AMEND"));
        post(path, mechanic, f.workshop(), Map.of("amendsSeq", 2, "reason", "Corrección de mi propio trabajo", "mileage", 1499))
                .andExpect(status().isCreated());
        var otherMechanic = addMember(f.owner(), f.workshop(), "MECHANIC");
        post(path, otherMechanic, f.workshop(), Map.of("amendsSeq", 2, "reason", "Intento sobre trabajo ajeno", "mileage", 1450))
                .andExpect(status().isForbidden());
        var stranger = registerAndLogin();
        UUID otherWs = createWorkshop(stranger);
        post(path, stranger, otherWs, Map.of("amendsSeq", 2, "reason", "Intento desde otro taller", "mileage", 1)).andExpect(status().isNotFound());
        assertThat(history.verify(f.motorcycle()).isValid()).isTrue();
    }

    @Test
    void odometerChangeNeedsPhotoAndReasonAndResetsTheReference() throws Exception {
        var f = fixture();
        String path = "/api/v1/motorcycles/" + f.motorcycle() + "/history/odometer-changes";
        post(path, f.owner(), f.workshop(), Map.of("mileage", 10, "reason", "Se cambió el tablero por falla", "photoId", UUID.randomUUID()))
                .andExpect(status().isNotFound()); // photo that does not exist in this workshop
        var noPhoto = new HashMap<String, Object>();
        noPhoto.put("mileage", 10);
        noPhoto.put("reason", "Se cambió el tablero por falla");
        post(path, f.owner(), f.workshop(), noPhoto).andExpect(jsonPath("$.code").value("PHOTO_REQUIRED"));
        post(path, f.owner(), f.workshop(), Map.of("mileage", 10, "reason", "corto", "photoId", uploadPhoto(f.owner(), f.workshop())))
                .andExpect(status().isUnprocessableContent());
        post(path, f.owner(), f.workshop(), Map.of("mileage", 10, "reason", "Se cambió el tablero por falla", "photoId", uploadPhoto(f.owner(), f.workshop())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.seq").value(2));
        assertThat(history.lastMileage(f.motorcycle()).orElseThrow().km()).isEqualTo(10);
        assertThat(history.verify(f.motorcycle()).isValid()).isTrue();
    }

    @Test
    void entriesFromAVerifiedWorkshopAreMarkedVerifiedFromThenOn() throws Exception {
        var f = fixture();
        verifyWorkshop(registerAdmin(), f.workshop());
        publish(delivered(f, UUID.randomUUID(), 1500, UUID.randomUUID()));
        awaitEntries(f.motorcycle(), 2);
        var rows = jdbc.sql("SELECT seq, public_content FROM history.history_entry WHERE motorcycle_id = ? ORDER BY seq").param(f.motorcycle()).query().listOfRows();
        assertThat((String) rows.get(0).get("public_content")).contains("\"workshopVerified\":false"); // earlier entries are never rewritten
        assertThat((String) rows.get(1).get("public_content")).contains("\"workshopVerified\":true");
    }
}
