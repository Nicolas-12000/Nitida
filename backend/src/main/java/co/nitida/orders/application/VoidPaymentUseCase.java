package co.nitida.orders.application;

import co.nitida.orders.OrderStatus;
import co.nitida.orders.PaymentVoided;
import co.nitida.orders.domain.PaymentRepository;
import co.nitida.shared.ApiException;
import co.nitida.shared.Role;
import co.nitida.shared.WorkshopContext;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VoidPaymentUseCase {
    public record Command(WorkshopContext ctx, UUID paymentId, String reason) {}

    private final PaymentRepository payments;
    private final OrderAccess access;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    VoidPaymentUseCase(PaymentRepository payments, OrderAccess access, ApplicationEventPublisher events, Clock clock) {
        this.payments = payments;
        this.access = access;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public void execute(Command c) {
        c.ctx().require(Role.WORKSHOP_OWNER, Role.FRONT_DESK);
        var payment = payments.findByIdAndWorkshopId(c.paymentId(), c.ctx().workshopId()).orElseThrow(ApiException::notFound);
        var order = access.loadLocked(payment.getWorkOrderId(), c.ctx());
        if (order.getStatus() == OrderStatus.DELIVERED) {
            throw ApiException.conflict("ORDER_NOT_EDITABLE", "La orden ya fue entregada: sus pagos no se pueden anular");
        }
        var now = clock.instant();
        payment.voidIt(c.reason(), now);
        events.publishEvent(new PaymentVoided(payment.getId(), order.getId(), c.ctx().workshopId(), payment.getMethod().name(), payment.getAmount(), now));
    }
}
