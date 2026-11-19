package co.nitida.catalog.application;

import co.nitida.catalog.domain.SerialRepository;
import co.nitida.shared.ApiException;
import co.nitida.shared.Role;
import co.nitida.shared.WorkshopContext;
import co.nitida.shared.audit.AuditService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RN-94: only the workshop that registered a serial can retire it; any other gets a generic 404. */
@Service
public class RetireSerialUseCase {
    public record Command(WorkshopContext ctx, UUID serialId, String reason) {}

    private final SerialRepository serials;
    private final AuditService audit;
    private final Clock clock;

    RetireSerialUseCase(SerialRepository serials, AuditService audit, Clock clock) {
        this.serials = serials;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public void execute(Command c) {
        c.ctx().require(Role.WORKSHOP_OWNER, Role.MECHANIC);
        var serial = serials.findById(c.serialId()).filter(s -> s.getWorkshopId().equals(c.ctx().workshopId()))
                .orElseThrow(ApiException::notFound);
        serial.retire(c.reason(), clock.instant());
        audit.record(c.ctx().workshopId(), c.ctx().userId(), "SERIAL_RETIRED", "serial", serial.getId(), null);
    }
}
