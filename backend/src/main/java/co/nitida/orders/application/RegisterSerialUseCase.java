package co.nitida.orders.application;

import co.nitida.catalog.CatalogApi;
import co.nitida.catalog.CatalogApi.SerialRegistration;
import co.nitida.shared.RateLimiter;
import co.nitida.shared.Role;
import co.nitida.shared.WorkshopContext;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-23 / RN-94. A rejected serial is a normal outcome, not an error: the part stays without serial and the
 * transaction commits, so the SerialRejected event (alert F6) is not lost. The answer never says where it is active.
 */
@Service
public class RegisterSerialUseCase {
    public record Command(WorkshopContext ctx, UUID orderId, UUID partId, String serial, String reactivationReason) {}

    public record Result(boolean accepted, String evidenceLevel, String message) {}

    static final String REJECTED_MESSAGE = "Este serial no se puede registrar en esta moto. El repuesto quedó sin serial.";

    private final OrderAccess access;
    private final CatalogApi catalog;
    private final Clock clock;
    private final RateLimiter limiter;

    RegisterSerialUseCase(OrderAccess access, CatalogApi catalog, Clock clock) {
        this.access = access;
        this.catalog = catalog;
        this.clock = clock;
        this.limiter = new RateLimiter(30, Duration.ofMinutes(10).toMillis(), clock); // stops probing for where a serial lives
    }

    @Transactional
    public Result execute(Command c) {
        c.ctx().require(Role.WORKSHOP_OWNER, Role.MECHANIC);
        limiter.check(c.ctx().userId().toString());
        var order = access.load(c.orderId(), c.ctx());
        var part = order.findPart(c.partId());
        var outcome = catalog.registerSerial(c.ctx().workshopId(), order.getMotorcycleId(), order.getId(), c.serial(), c.reactivationReason());
        if (outcome instanceof SerialRegistration.Accepted accepted) {
            // A correction replaces the serial: the mistyped one must stop blocking that serial everywhere.
            if (part.getSerialId() != null && !part.getSerialId().equals(accepted.serialId())) {
                catalog.releaseSerial(part.getSerialId(), order.getId());
            }
            part.attachSerial(accepted.serialId(), normalize(c.serial()));
            order.markChanged(clock.instant());
            return new Result(true, part.getEvidenceLevel().name(), null);
        }
        return new Result(false, part.getEvidenceLevel().name(), REJECTED_MESSAGE);
    }

    private static String normalize(String raw) {
        return raw.replaceAll("\\s", "").toUpperCase();
    }
}
