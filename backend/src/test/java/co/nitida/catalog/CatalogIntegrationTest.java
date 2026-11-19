package co.nitida.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.nitida.catalog.CatalogApi.SerialRegistration;
import co.nitida.shared.ApiException;
import co.nitida.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CatalogIntegrationTest extends IntegrationTest {
    @Autowired CatalogApi catalog;

    private static String serial() { return "SN-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(); }

    @Test
    void sameSerialOnSameMotorcycleIsAcceptedAgainWithoutAlert() {
        UUID moto = UUID.randomUUID(), ws = UUID.randomUUID();
        String s = serial();
        var first = catalog.registerSerial(ws, moto, UUID.randomUUID(), s, null);
        var second = catalog.registerSerial(ws, moto, UUID.randomUUID(), s.toLowerCase(), null);
        assertThat(first).isInstanceOf(SerialRegistration.Accepted.class);
        assertThat(second).isInstanceOf(SerialRegistration.Accepted.class);
        assertThat(((SerialRegistration.Accepted) second).serialId()).isEqualTo(((SerialRegistration.Accepted) first).serialId());
    }

    @Test
    void sameSerialOnAnotherMotorcycleIsRejectedWithoutLeakingAnything() {
        String s = serial();
        catalog.registerSerial(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), s, null);
        var result = catalog.registerSerial(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), s, null);
        assertThat(result).isInstanceOf(SerialRegistration.Rejected.class);
        // the only data in the rejection is the hash of what the caller already typed
        assertThat(result.toString()).doesNotContain(s);
    }

    @Test
    void retiredSerialNeedsAReasonToComeBackAndThenWorksElsewhere() {
        UUID moto = UUID.randomUUID(), other = UUID.randomUUID(), ws = UUID.randomUUID(), order = UUID.randomUUID();
        String s = serial();
        catalog.registerSerial(ws, moto, order, s, null);
        catalog.releaseForOrder(order);
        assertThatThrownBy(() -> catalog.registerSerial(ws, other, UUID.randomUUID(), s, null))
                .isInstanceOf(ApiException.class).extracting(e -> ((ApiException) e).code()).isEqualTo("REACTIVATION_REASON_REQUIRED");
        var back = catalog.registerSerial(ws, other, UUID.randomUUID(), s, "Pieza reparada y reinstalada");
        assertThat(back).isInstanceOf(SerialRegistration.Accepted.class);
        assertThat(catalog.registerSerial(ws, moto, UUID.randomUUID(), s, null)).isInstanceOf(SerialRegistration.Rejected.class);
    }

    /** Two workshops race for one serial on different motorcycles: exactly one wins, the other is rejected. */
    @Test
    void concurrentRegistrationHasExactlyOneWinner() throws Exception {
        String s = serial();
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        List<Callable<SerialRegistration>> tasks = List.of(
                () -> { start.await(); return catalog.registerSerial(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), s, null); },
                () -> { start.await(); return catalog.registerSerial(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), s, null); });
        var futures = tasks.stream().map(pool::submit).toList();
        start.countDown();
        long accepted = 0, rejected = 0;
        for (var f : futures) {
            var r = f.get();
            if (r instanceof SerialRegistration.Accepted) accepted++; else rejected++;
        }
        pool.shutdown();
        assertThat(accepted).isEqualTo(1);
        assertThat(rejected).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM catalog.serial WHERE normalized_serial = ? AND status = 'ACTIVE'").param(s).query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    void scanAutocompletesFromTheWorkshopCatalogAndRejectsBadCodes() throws Exception {
        var owner = registerAndLogin();
        UUID ws = createWorkshop(owner);
        get("/api/v1/catalog/products/4006381333931", owner, ws).andExpect(status().isOk()).andExpect(jsonPath("$.found").value(false));
        post("/api/v1/catalog/products", owner, ws, Map.of("code", "4006381333931", "name", "Aceite 20W50", "brand", "Motul"))
                .andExpect(status().isCreated());
        get("/api/v1/catalog/products/4006381333931", owner, ws).andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.product.name").value("Aceite 20W50"));
        get("/api/v1/catalog/products/4006381333932", owner, ws).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_PRODUCT_CODE"));
        // catalog is per workshop (RN-93)
        var other = registerAndLogin();
        UUID ws2 = createWorkshop(other);
        get("/api/v1/catalog/products/4006381333931", other, ws2).andExpect(jsonPath("$.found").value(false));
    }

    @Test
    void frontDeskCannotScanOrProposeProducts() throws Exception {
        var owner = registerAndLogin();
        UUID ws = createWorkshop(owner);
        var desk = addMember(owner, ws, "FRONT_DESK");
        get("/api/v1/catalog/products/4006381333931", desk, ws).andExpect(status().isForbidden());
    }
}
