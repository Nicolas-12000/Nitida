package co.nitida.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.nitida.support.OrderFlowTest;
import java.util.ArrayList;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

/** Listings page by cursor: stable, no gaps, no repeats; the array response stays the same (spec §9). */
class OrderPaginationIntegrationTest extends OrderFlowTest {

    @Test
    void followingTheCursorWalksEveryOrderOnceAndEndsWithoutACursor() throws Exception {
        var s = newShop();
        var created = new HashSet<String>();
        for (int i = 0; i < 5; i++) created.add(openOrder(s, 100).orderId().toString());

        var seen = new ArrayList<String>();
        String cursor = null;
        int pages = 0;
        do {
            var res = get("/api/v1/orders?limit=2" + (cursor == null ? "" : "&cursor=" + cursor), s.owner(), s.ws()).andExpect(status().isOk()).andReturn().getResponse();
            var items = co.nitida.shared.Json.mapper().readTree(res.getContentAsString());
            assertThat(items.size()).isBetween(1, 2);
            items.forEach(n -> seen.add(n.get("id").asString()));
            cursor = res.getHeader("X-Next-Cursor");
            pages++;
        } while (cursor != null && pages < 10);

        assertThat(pages).isEqualTo(3);
        assertThat(seen).hasSize(5).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(created);
    }

    @Test
    void aFullPageWithNothingAfterItHasNoCursor() throws Exception {
        var s = newShop();
        openOrder(s, 100);
        openOrder(s, 100);
        var res = get("/api/v1/orders?limit=2", s.owner(), s.ws()).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(res.getHeader("X-Next-Cursor")).isNull();
    }

    @Test
    void badLimitsAndCursorsAreRejectedAsDataNotExecuted() throws Exception {
        var s = newShop();
        for (String limit : new String[] {"0", "-1", "101", "abc"}) {
            get("/api/v1/orders?limit=" + limit, s.owner(), s.ws()).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("INVALID_LIMIT"));
        }
        get("/api/v1/orders?cursor=esto-no-es-un-cursor", s.owner(), s.ws()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        get("/api/v1/orders?cursor=" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(("2026-01-01T00:00:00Z|" + "'; DROP TABLE x;--").getBytes()),
                s.owner(), s.ws()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
    }

    @Test
    void aCursorFromAnotherWorkshopRevealsNothingOfIt() throws Exception {
        var a = newShop();
        var b = newShop();
        for (int i = 0; i < 3; i++) openOrder(a, 100);
        var first = get("/api/v1/orders?limit=1", a.owner(), a.ws()).andReturn().getResponse();
        String cursorOfA = first.getHeader("X-Next-Cursor");
        assertThat(cursorOfA).isNotNull();
        get("/api/v1/orders?cursor=" + cursorOfA, b.owner(), b.ws()).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }
}
