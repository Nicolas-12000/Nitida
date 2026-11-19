package co.nitida.support;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpMethod;

/** Reusable steps of the order flow for tests that need orders, payments or alerts as a starting point. */
public abstract class OrderFlowTest extends IntegrationTest {
    private static int plateSeq = 0;

    public record Shop(Session owner, Session mechanic, Session desk, UUID ws) {}

    public record Visit(Shop shop, UUID intakeId, UUID motorcycleId, UUID orderId) {}

    protected Shop newShop() throws Exception {
        var owner = registerAndLogin();
        UUID ws = createWorkshop(owner);
        return new Shop(owner, addMember(owner, ws, "MECHANIC"), addMember(owner, ws, "FRONT_DESK"), ws);
    }

    protected String nextPlate() {
        int n = plateSeq++;
        return "FL" + (char) ('A' + (n / 100) % 26) + String.format("%02d", n % 100) + "K";
    }

    protected UUID receive(Shop s, String plate, int km) throws Exception {
        var body = new HashMap<String, Object>();
        body.put("plate", plate);
        body.put("brand", "Yamaha");
        body.put("line", "FZ 2.0");
        body.put("mileage", km);
        body.put("odometerPhotoId", uploadPhoto(s.owner(), s.ws()));
        body.put("motorcyclePhotoId", uploadPhoto(s.owner(), s.ws()));
        return UUID.fromString(json(post("/api/v1/intakes", s.desk(), s.ws(), body).andExpect(status().isCreated())).get("intakeId").asString());
    }

    protected Visit openOrder(Shop s, int km) throws Exception {
        UUID intakeId = receive(s, nextPlate(), km);
        var res = json(post("/api/v1/orders", s.mechanic(), s.ws(), Map.of("intakeId", intakeId, "diagnosis", "Ruido en la transmisión al acelerar"))
                .andExpect(status().isCreated()));
        return new Visit(s, intakeId, UUID.fromString(res.get("motorcycleId").asString()), UUID.fromString(res.get("orderId").asString()));
    }

    protected long version(Visit v) throws Exception {
        return json(get("/api/v1/orders/" + v.orderId(), v.shop().owner(), v.shop().ws())).get("version").asLong();
    }

    protected void setLaborAndJob(Visit v, long labor) throws Exception {
        call(HttpMethod.PATCH, "/api/v1/orders/" + v.orderId(), v.shop().mechanic(), v.shop().ws(),
                Map.of("version", version(v), "laborCost", labor,
                        "jobs", List.of(Map.of("type", "OIL_CHANGE", "description", "Cambio de aceite")))).andExpect(status().isNoContent());
    }

    protected void move(Visit v, String target) throws Exception {
        var body = new HashMap<String, Object>();
        body.put("status", target);
        body.put("version", version(v));
        post("/api/v1/orders/" + v.orderId() + "/transitions", v.shop().mechanic(), v.shop().ws(), body).andExpect(status().isOk());
    }

    /** An order with the given labor cost, ready to receive payments. */
    protected Visit orderWorth(Shop s, long labor) throws Exception {
        var v = openOrder(s, 1000);
        setLaborAndJob(v, labor);
        move(v, "IN_PROGRESS");
        move(v, "COMPLETED");
        return v;
    }

    protected UUID pay(Visit v, String method, long amount) throws Exception {
        return UUID.fromString(json(post("/api/v1/orders/" + v.orderId() + "/payments", v.shop().desk(), v.shop().ws(),
                Map.of("method", method, "amount", amount)).andExpect(status().isCreated())).get("paymentId").asString());
    }

    protected void deliver(Visit v) throws Exception {
        post("/api/v1/orders/" + v.orderId() + "/delivery", v.shop().desk(), v.shop().ws(), Map.of("version", version(v))).andExpect(status().isOk());
    }
}
