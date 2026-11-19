package co.nitida.orders.application;

import co.nitida.orders.OrderStatus;
import co.nitida.orders.application.OrderViews.OrderSummary;
import co.nitida.orders.application.OrderViews.OrderView;
import co.nitida.orders.domain.PaymentRepository;
import co.nitida.orders.domain.WorkOrder;
import co.nitida.orders.domain.WorkOrderRepository;
import co.nitida.shared.Page;
import co.nitida.shared.Role;
import co.nitida.shared.WorkshopContext;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side for the dashboard (kanban by status) and the order screen. */
@Service
public class QueryWorkOrdersUseCase {
    private final OrderAccess access;
    private final WorkOrderRepository orders;
    private final PaymentRepository payments;

    QueryWorkOrdersUseCase(OrderAccess access, WorkOrderRepository orders, PaymentRepository payments) {
        this.access = access;
        this.orders = orders;
        this.payments = payments;
    }

    @Transactional(readOnly = true)
    public OrderView detail(WorkshopContext ctx, UUID orderId) {
        var order = access.load(orderId, ctx);
        boolean money = ctx.has(Role.WORKSHOP_OWNER) || ctx.has(Role.FRONT_DESK);
        return OrderViews.of(order, money ? payments.findByWorkOrderIdOrderByRecordedAtAsc(orderId) : List.of(), money);
    }

    @Transactional(readOnly = true)
    public Page<OrderSummary> list(WorkshopContext ctx, Set<OrderStatus> statuses, int limit, Page.Position after) {
        Set<OrderStatus> wanted = statuses.isEmpty() ? EnumSet.of(OrderStatus.OPEN, OrderStatus.IN_PROGRESS, OrderStatus.COMPLETED) : statuses;
        var size = PageRequest.of(0, limit + 1);
        var rows = after == null
                ? orders.findByWorkshopIdAndStatusInOrderByCreatedAtDescIdDesc(ctx.workshopId(), wanted, size)
                : orders.pageAfter(ctx.workshopId(), wanted, after.at(), after.id(), size);
        return Page.of(rows, limit, WorkOrder::getCreatedAt, WorkOrder::getId,
                o -> new OrderSummary(o.getId(), o.getMotorcycleId(), o.getStatus(), o.getDiagnosis(), o.total().value(), o.getUpdatedAt()));
    }
}
