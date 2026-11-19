package co.nitida.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.nitida.support.OrderFlowTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** A mechanic who typed a serial wrong and corrects it must not leave the wrong one blocking that serial everywhere. */
class SerialCorrectionIntegrationTest extends OrderFlowTest {

    private UUID addPart(Shop s, Visit v) throws Exception {
        return UUID.fromString(json(post("/api/v1/orders/" + v.orderId() + "/parts", s.mechanic(), s.ws(),
                Map.of("name", "Batería", "brand", "Marca", "condition", "OEM", "quantity", 1, "unitPrice", 1_000))
                .andExpect(status().isCreated())).get("partId").asString());
    }

    @Test
    void correctingASerialFreesTheMistypedOne() throws Exception {
        var a = newShop();
        var visitA = openOrder(a, 100);
        UUID part = addPart(a, visitA);
        String wrong = "TYPO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String right = "REAL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        post("/api/v1/orders/" + visitA.orderId() + "/parts/" + part + "/serial", a.mechanic(), a.ws(), Map.of("serial", wrong))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accepted").value(true));
        post("/api/v1/orders/" + visitA.orderId() + "/parts/" + part + "/serial", a.mechanic(), a.ws(), Map.of("serial", right))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accepted").value(true));

        // someone else legitimately owns a part with the serial that was only a typo
        var b = newShop();
        var visitB = openOrder(b, 100);
        UUID partB = addPart(b, visitB);
        // RN-94: a retired serial needs a reason to come back; it is no longer "active elsewhere", so no rejection and no F6
        post("/api/v1/orders/" + visitB.orderId() + "/parts/" + partB + "/serial", b.mechanic(), b.ws(), Map.of("serial", wrong))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("REACTIVATION_REASON_REQUIRED"));
        post("/api/v1/orders/" + visitB.orderId() + "/parts/" + partB + "/serial", b.mechanic(), b.ws(),
                Map.of("serial", wrong, "reactivationReason", "Pieza nueva con ese serial"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accepted").value(true));

        assertThat(jdbc.sql("SELECT count(*) FROM catalog.serial WHERE normalized_serial = ? AND status = 'ACTIVE'").param(right).query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    void sendingTheSameSerialAgainChangesNothing() throws Exception {
        var a = newShop();
        var visit = openOrder(a, 100);
        UUID part = addPart(a, visit);
        String serial = "SAME-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        for (int i = 0; i < 2; i++) {
            post("/api/v1/orders/" + visit.orderId() + "/parts/" + part + "/serial", a.mechanic(), a.ws(), Map.of("serial", serial))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.accepted").value(true));
        }
        assertThat(jdbc.sql("SELECT count(*) FROM catalog.serial WHERE normalized_serial = ? AND status = 'ACTIVE'").param(serial).query(Long.class).single()).isEqualTo(1L);
    }
}
