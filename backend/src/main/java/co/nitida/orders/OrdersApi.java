package co.nitida.orders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Public API of the orders module (queries for the cash desk). */
public interface OrdersApi {
    record StalledOrder(UUID orderId, UUID workshopId, OrderStatus status, Instant lastChangedAt) {}

    /** OPEN or IN_PROGRESS orders whose last change is before the cut-off (leak alert F5). */
    List<StalledOrder> stalledBefore(Instant cutoff);

    /** Sum of non-voided CASH payments recorded in [from, to) for the workshop (RN-52). */
    long cashTotal(UUID workshopId, Instant from, Instant to);

    record ZeroTotalOrder(UUID orderId, UUID workshopId) {}

    /** Delivered orders with a zero total that are not warranty orders: should never exist (alert F4). */
    List<ZeroTotalOrder> inconsistentZeroTotalOrders();
}
