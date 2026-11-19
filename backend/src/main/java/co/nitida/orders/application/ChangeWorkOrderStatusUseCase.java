package co.nitida.orders.application;

import co.nitida.catalog.CatalogApi;
import co.nitida.orders.OrderStatus;
import co.nitida.orders.WorkOrderCancelled;
import co.nitida.shared.Role;
import co.nitida.shared.WorkshopContext;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-09 / RN-40. Cancelling frees the order's serials (RN-94) and publishes WorkOrderCancelled for alert F2 (RN-49). */
@Service
public class ChangeWorkOrderStatusUseCase {
    public record Command(WorkshopContext ctx, UUID orderId, OrderStatus target, String reason, long version) {}

    private final OrderAccess access;
    private final CatalogApi catalog;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    ChangeWorkOrderStatusUseCase(OrderAccess access, CatalogApi catalog, ApplicationEventPublisher events, Clock clock) {
        this.access = access;
        this.catalog = catalog;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public OrderStatus execute(Command c) {
        c.ctx().require(Role.WORKSHOP_OWNER, Role.MECHANIC);
        var order = access.load(c.orderId(), c.ctx());
        order.checkVersion(c.version());
        order.transition(c.target(), c.reason(), clock.instant());
        if (order.getStatus() == OrderStatus.CANCELLED) {
            catalog.releaseForOrder(order.getId());
            events.publishEvent(new WorkOrderCancelled(order.getId(), order.getWorkshopId(), order.hasParts(), c.reason()));
        }
        return order.getStatus();
    }
}
