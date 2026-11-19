package co.nitida.orders;

import static org.assertj.core.api.Assertions.assertThat;

import co.nitida.support.OrderFlowTest;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

/** Two front-desk users register the full amount at the same moment: only one may succeed (RN-51, RN-47). */
class PaymentRaceIntegrationTest extends OrderFlowTest {

    @Test
    void simultaneousPaymentsCannotTogetherExceedTheTotal() throws Exception {
        var shop = newShop();
        var visit = orderWorth(shop, 100_000);
        int threads = 6;
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(threads);
        var results = new ArrayList<java.util.concurrent.Future<Integer>>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return post("/api/v1/orders/" + visit.orderId() + "/payments", shop.desk(), shop.ws(),
                        Map.of("method", "CASH", "amount", 100_000)).andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        int created = 0;
        for (var f : results) if (f.get() == 201) created++;
        pool.shutdown();

        assertThat(created).as("payments accepted for a $100.000 order").isEqualTo(1);
        assertThat(jdbc.sql("SELECT coalesce(sum(amount),0) FROM orders.payment WHERE work_order_id = ? AND voided_at IS NULL")
                .param(visit.orderId()).query(Long.class).single()).isEqualTo(100_000L);
    }
}
