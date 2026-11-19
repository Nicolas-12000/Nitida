package co.nitida.cashdesk.application;

import co.nitida.cashdesk.domain.AlertType;
import co.nitida.cashdesk.domain.LeakAlert;
import co.nitida.cashdesk.domain.LeakAlertRepository;
import co.nitida.shared.Page;
import co.nitida.shared.Role;
import co.nitida.shared.WorkshopContext;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-18 (read): pending or resolved alerts of the active workshop, newest first. */
@Service
public class ListLeakAlertsUseCase {
    public enum Filter { PENDING, RESOLVED }

    public record AlertView(UUID id, AlertType type, UUID referenceId, String details, Instant createdAt,
                            Instant resolvedAt, String comment) {}

    private final LeakAlertRepository alerts;

    ListLeakAlertsUseCase(LeakAlertRepository alerts) {
        this.alerts = alerts;
    }

    @Transactional(readOnly = true)
    public Page<AlertView> execute(WorkshopContext ctx, Filter filter, int limit, Page.Position after) {
        ctx.require(Role.WORKSHOP_OWNER);
        boolean resolved = filter == Filter.RESOLVED;
        var size = PageRequest.of(0, limit + 1);
        var rows = after == null ? alerts.firstPage(ctx.workshopId(), resolved, size)
                : alerts.pageAfter(ctx.workshopId(), resolved, after.at(), after.id(), size);
        return Page.of(rows, limit, LeakAlert::getCreatedAt, LeakAlert::getId, ListLeakAlertsUseCase::view);
    }

    private static AlertView view(LeakAlert a) {
        return new AlertView(a.getId(), a.getType(), a.getReferenceId(), a.getDetails(), a.getCreatedAt(), a.getResolvedAt(), a.getComment());
    }
}
