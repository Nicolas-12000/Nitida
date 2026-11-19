package co.nitida.orders.application;

import co.nitida.files.FilesApi;
import co.nitida.motorcycles.MotorcyclesApi;
import co.nitida.orders.domain.PaymentRepository;
import co.nitida.shared.CopAmount;
import co.nitida.shared.Role;
import co.nitida.shared.WorkshopContext;
import co.nitida.shared.crypto.SecureTokens;
import co.nitida.shared.events.OrderDelivered;
import co.nitida.workshops.WorkshopsApi;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-11: the only way to DELIVERED. Optimistic locking, payment check, claim code, snapshot event (RN-47, RN-48, RN-80). */
@Service
public class DeliverOrderUseCase {
    public record Command(WorkshopContext ctx, UUID orderId, long version) {}

    /** The claim code is returned in clear exactly once; only its hash is stored. */
    public record Result(UUID orderId, CopAmount total, String claimCode, Instant claimCodeExpiresAt) {}

    static final Duration CLAIM_CODE_VALIDITY = Duration.ofDays(30);

    private final OrderAccess access;
    private final PaymentRepository payments;
    private final WorkshopsApi workshops;
    private final FilesApi files;
    private final MotorcyclesApi motorcycles;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    DeliverOrderUseCase(OrderAccess access, PaymentRepository payments, WorkshopsApi workshops, FilesApi files,
                        MotorcyclesApi motorcycles, ApplicationEventPublisher events, Clock clock) {
        this.access = access;
        this.payments = payments;
        this.workshops = workshops;
        this.files = files;
        this.motorcycles = motorcycles;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public Result execute(Command c) {
        c.ctx().require(Role.WORKSHOP_OWNER, Role.FRONT_DESK);
        workshops.requireActive(c.ctx().workshopId());
        var order = access.loadLocked(c.orderId(), c.ctx()); // delivery sums the payments: a late one must not slip in
        order.checkVersion(c.version());
        var now = clock.instant();
        order.deliver(payments.findByWorkOrderIdOrderByRecordedAtAsc(order.getId()), now);

        String code = SecureTokens.newCode(8);
        order.assignClaimCode(SecureTokens.hash(code));
        Instant expires = now.plus(CLAIM_CODE_VALIDITY);
        motorcycles.issueClaimCode(order.getMotorcycleId(), SecureTokens.hash(code), expires);

        var hashes = order.getPhotos().stream().map(p -> files.sha256(p.getFileId(), c.ctx().workshopId())).toList();
        OrderDelivered snapshot = order.snapshot(hashes);
        events.publishEvent(snapshot); // same transaction: Modulith's registry guarantees delivery to history
        return new Result(order.getId(), order.total(), code, expires);
    }
}
