package co.nitida.orders.application;

import co.nitida.orders.PaymentRegistered;
import co.nitida.orders.domain.Payment;
import co.nitida.orders.domain.PaymentMethod;
import co.nitida.orders.domain.PaymentRepository;
import co.nitida.shared.Role;
import co.nitida.shared.UuidV7;
import co.nitida.shared.WorkshopContext;
import co.nitida.workshops.WorkshopsApi;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-10 / RN-51. */
@Service
public class RegisterPaymentUseCase {
    public record Command(WorkshopContext ctx, UUID orderId, PaymentMethod method, long amount) {}

    private final OrderAccess access;
    private final PaymentRepository payments;
    private final WorkshopsApi workshops;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    RegisterPaymentUseCase(OrderAccess access, PaymentRepository payments, WorkshopsApi workshops, ApplicationEventPublisher events, Clock clock) {
        this.access = access;
        this.payments = payments;
        this.workshops = workshops;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public UUID execute(Command c) {
        c.ctx().require(Role.WORKSHOP_OWNER, Role.FRONT_DESK);
        workshops.requireActive(c.ctx().workshopId());
        var order = access.loadLocked(c.orderId(), c.ctx()); // two desks paying at once must not both pass the total check
        var existing = payments.findByWorkOrderIdOrderByRecordedAtAsc(order.getId());
        long alreadyPaid = existing.stream().filter(p -> !p.isVoided()).mapToLong(Payment::getAmount).sum();
        var now = clock.instant();
        var payment = new Payment(UuidV7.next(), c.ctx().workshopId(), order.getId(), c.method(), c.amount(), c.ctx().userId(), now);
        order.checkCanReceivePayment(c.method(), payment.getAmount(), alreadyPaid);
        payments.save(payment);
        events.publishEvent(new PaymentRegistered(payment.getId(), order.getId(), c.ctx().workshopId(), c.method().name(), payment.getAmount(), now));
        return payment.getId();
    }
}
