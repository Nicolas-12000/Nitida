package co.nitida.orders.application;

import co.nitida.orders.OrderStatus;
import co.nitida.orders.OrdersApi;
import co.nitida.orders.domain.PaymentMethod;
import co.nitida.orders.domain.PaymentRepository;
import co.nitida.orders.domain.WorkOrderRepository;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OrdersApiImpl implements OrdersApi {
    private final WorkOrderRepository orders;
    private final PaymentRepository payments;

    OrdersApiImpl(WorkOrderRepository orders, PaymentRepository payments) {
        this.orders = orders;
        this.payments = payments;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StalledOrder> stalledBefore(Instant cutoff) {
        return orders.findByStatusInAndUpdatedAtBefore(EnumSet.of(OrderStatus.OPEN, OrderStatus.IN_PROGRESS), cutoff).stream()
                .map(o -> new StalledOrder(o.getId(), o.getWorkshopId(), o.getStatus(), o.getUpdatedAt())).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long cashTotal(UUID workshopId, Instant from, Instant to) {
        return payments.sumByMethod(workshopId, PaymentMethod.CASH, from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> inconsistentZeroTotalOrders(UUID workshopId) {
        return orders.findByWorkshopIdAndStatusInOrderByCreatedAtDesc(workshopId, EnumSet.of(OrderStatus.DELIVERED),
                        org.springframework.data.domain.Pageable.unpaged()).stream()
                .filter(o -> o.total().value() == 0 && !o.isWarranty()).map(o -> o.getId()).toList();
    }
}
