package co.nitida.cashdesk.application;

import co.nitida.cashdesk.domain.LeakAlertRepository;
import co.nitida.shared.ApiException;
import co.nitida.shared.Role;
import co.nitida.shared.WorkshopContext;
import co.nitida.shared.audit.AuditService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-18: the owner attends an alert once, with a comment; alerts are never deleted (RN-53). */
@Service
public class ResolveLeakAlertUseCase {
    public record Command(WorkshopContext ctx, UUID alertId, String comment) {}

    private final LeakAlertRepository alerts;
    private final AuditService audit;
    private final Clock clock;

    ResolveLeakAlertUseCase(LeakAlertRepository alerts, AuditService audit, Clock clock) {
        this.alerts = alerts;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public void execute(Command c) {
        var ctx = c.ctx().require(Role.WORKSHOP_OWNER);
        var alert = alerts.findByIdAndWorkshopId(c.alertId(), ctx.workshopId()).orElseThrow(ApiException::notFound);
        alert.resolve(ctx.userId(), c.comment(), clock.instant());
        audit.record(ctx.workshopId(), ctx.userId(), "LEAK_ALERT_RESOLVED", "leak_alert", alert.getId(), null);
    }
}
