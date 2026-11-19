package co.nitida.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.nitida.support.OrderFlowTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-20 (claim) and UC-21 (transfer), RN-80..82. */
class OwnerPortalIntegrationTest extends OrderFlowTest {

    private record Delivered(Visit visit, String code) {}

    private Delivered delivered() throws Exception {
        var s = newShop();
        var v = orderWorth(s, 50_000);
        pay(v, "CASH", 50_000);
        return new Delivered(v, deliver(v));
    }

    private org.springframework.test.web.servlet.ResultActions claim(Session who, String code) throws Exception {
        return post("/api/v1/owner/claims", who, null, Map.of("code", code));
    }

    private int entries(Session who, UUID moto) throws Exception {
        return json(get("/api/v1/owner/motorcycles/" + moto + "/history", who, null).andExpect(status().isOk())).get("entries").size();
    }

    @Test
    void theOwnerClaimsWithTheReceiptCodeAndSeesTheVerifiedHistory() throws Exception {
        var d = delivered();
        var owner = registerAndLogin();
        claim(owner, d.code().toLowerCase()).andExpect(status().isOk()).andExpect(jsonPath("$.motorcycleId").value(d.visit().motorcycleId().toString()));
        get("/api/v1/owner/motorcycles", owner, null).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].motorcycleId").value(d.visit().motorcycleId().toString()))
                .andExpect(jsonPath("$[0].active").value(true));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(entries(owner, d.visit().motorcycleId())).isEqualTo(2));
        get("/api/v1/owner/motorcycles/" + d.visit().motorcycleId() + "/history", owner, null).andExpect(jsonPath("$.verification.status").value("VALID"));
    }

    @Test
    void usedExpiredAndUnknownCodesAreIndistinguishable() throws Exception {
        var used = delivered();
        claim(registerAndLogin(), used.code()).andExpect(status().isOk());
        var expired = delivered();
        jdbc.sql("UPDATE motorcycles.claim_code SET expires_at = ? WHERE motorcycle_id = ?")
                .params(Timestamp.from(Instant.now().minusSeconds(1)), expired.visit().motorcycleId()).update();

        var someone = registerAndLogin();
        var bodies = new java.util.ArrayList<String>();
        for (String code : new String[] {used.code(), expired.code(), "ZZZZZZZZ"}) {
            bodies.add(claim(someone, code).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString()
                    .replaceAll("\"traceId\":\"[^\"]*\"", ""));
        }
        assertThat(bodies).containsOnly(bodies.get(0));
    }

    @Test
    void aMotorcycleHasAtMostOneActiveOwner() throws Exception {
        var d = delivered();
        claim(registerAndLogin(), d.code()).andExpect(status().isOk());
        // a second delivery of the same motorcycle hands out another code: it cannot steal the ownership
        var again = openOrderFor(d.visit());
        String second = deliver(again);
        claim(registerAndLogin(), second).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_OWNED"));
        assertThat(jdbc.sql("SELECT count(*) FROM motorcycles.vehicle_owner WHERE motorcycle_id = ? AND valid_to IS NULL")
                .param(d.visit().motorcycleId()).query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    void transferMovesOwnershipAddsAnEntryWithoutNamesAndCutsThePreviousOwnerOff() throws Exception {
        var d = delivered();
        UUID moto = d.visit().motorcycleId();
        var seller = registerAndLogin();
        var buyer = registerAndLogin();
        claim(seller, d.code()).andExpect(status().isOk());
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(entries(seller, moto)).isEqualTo(2));

        post("/api/v1/owner/motorcycles/" + moto + "/transfers", buyer, null, null).andExpect(status().isNotFound()); // not the owner
        String code = json(post("/api/v1/owner/motorcycles/" + moto + "/transfers", seller, null, null).andExpect(status().isCreated()))
                .get("code").asString();
        post("/api/v1/owner/transfers/" + code + "/accept", seller, null, null).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SAME_OWNER"));
        post("/api/v1/owner/transfers/" + code + "/accept", buyer, null, null).andExpect(status().isOk());
        post("/api/v1/owner/transfers/" + code + "/accept", registerAndLogin(), null, null).andExpect(status().isNotFound()); // single use

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(entries(buyer, moto)).isEqualTo(3));
        var history = json(get("/api/v1/owner/motorcycles/" + moto + "/history", buyer, null));
        assertThat(history.get("entries").get(2).get("type").asString()).isEqualTo("OWNERSHIP_TRANSFER");
        assertThat(history.get("verification").get("status").asString()).isEqualTo("VALID");
        assertThat(history.toString()).doesNotContain(seller.email()).doesNotContain(buyer.email())
                .doesNotContain(seller.userId().toString()).doesNotContain(buyer.userId().toString());

        // the seller keeps what happened while they owned it, nothing after
        var later = openOrderFor(d.visit());
        deliver(later);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(entries(buyer, moto)).isEqualTo(4));
        assertThat(entries(seller, moto)).isEqualTo(2);
        get("/api/v1/owner/motorcycles", seller, null).andExpect(jsonPath("$[0].active").value(false));
        post("/api/v1/owner/motorcycles/" + moto + "/transfers", seller, null, null).andExpect(status().isNotFound());
    }

    @Test
    void theOwnerSharesAPublicLinkOnlyForTheirOwnMotorcycle() throws Exception {
        var d = delivered();
        var owner = registerAndLogin();
        claim(owner, d.code()).andExpect(status().isOk());
        String token = json(post("/api/v1/owner/motorcycles/" + d.visit().motorcycleId() + "/links", owner, null, Map.of("days", 7))
                .andExpect(status().isCreated())).get("token").asString();
        get("/api/v1/public/v/" + token, null, null).andExpect(status().isOk());
        post("/api/v1/owner/motorcycles/" + d.visit().motorcycleId() + "/links", registerAndLogin(), null, Map.of("days", 7))
                .andExpect(status().isNotFound());
    }

    @Test
    void aStrangerCannotReadSomeoneElsesMotorcycleAndAnonymousIsRejected() throws Exception {
        var d = delivered();
        claim(registerAndLogin(), d.code()).andExpect(status().isOk());
        get("/api/v1/owner/motorcycles/" + d.visit().motorcycleId() + "/history", registerAndLogin(), null).andExpect(status().isNotFound());
        get("/api/v1/owner/motorcycles", null, null).andExpect(status().isUnauthorized());
    }

    @Test
    void claimAttemptsAreRateLimitedPerUser() throws Exception {
        var guesser = registerAndLogin();
        for (int i = 0; i < 10; i++) claim(guesser, "AAAAAA" + (char) ('A' + i) + "A").andExpect(status().isNotFound());
        claim(guesser, "BBBBBBBB").andExpect(status().isTooManyRequests());
    }

    /** A new order on an already-known motorcycle (same plate), completed and paid, ready to deliver. */
    private Visit openOrderFor(Visit previous) throws Exception {
        var s = previous.shop();
        String plate = jdbc.sql("SELECT current_plate FROM motorcycles.motorcycle WHERE id = ?").param(previous.motorcycleId()).query(String.class).single();
        UUID intake = receive(s, plate, 2000);
        var res = json(post("/api/v1/orders", s.mechanic(), s.ws(), Map.of("intakeId", intake, "diagnosis", "Revisión general de rutina"))
                .andExpect(status().isCreated()));
        var v = new Visit(s, intake, previous.motorcycleId(), UUID.fromString(res.get("orderId").asString()));
        setLaborAndJob(v, 20_000);
        move(v, "IN_PROGRESS");
        move(v, "COMPLETED");
        pay(v, "CARD", 20_000);
        return v;
    }
}
