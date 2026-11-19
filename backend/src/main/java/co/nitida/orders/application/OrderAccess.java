package co.nitida.orders.application;

import co.nitida.orders.domain.WorkOrder;
import co.nitida.orders.domain.WorkOrderRepository;
import co.nitida.shared.ApiException;
import co.nitida.shared.WorkshopContext;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Loads an order of the active workshop; another workshop's order is simply "not found" (never revealed). */
@Component
class OrderAccess {
    private final WorkOrderRepository orders;

    OrderAccess(WorkOrderRepository orders) {
        this.orders = orders;
    }

    WorkOrder load(UUID orderId, WorkshopContext ctx) {
        return orders.findByIdAndWorkshopId(orderId, ctx.workshopId()).orElseThrow(ApiException::notFound);
    }

    /** For use cases that check the payments and then write one: concurrent callers wait for each other. */
    WorkOrder loadLocked(UUID orderId, WorkshopContext ctx) {
        return orders.lockByIdAndWorkshopId(orderId, ctx.workshopId()).orElseThrow(ApiException::notFound);
    }
}
