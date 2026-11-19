package co.nitida.cashdesk.application;

import co.nitida.cashdesk.domain.AlertType;
import co.nitida.cashdesk.domain.LeakAlert;
import co.nitida.cashdesk.domain.LeakAlertRepository;
import co.nitida.shared.Role;
import co.nitida.shared.WorkshopContext;
import java.time.Instant;
import java.util.List;
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

    // shortcut: fixed page of the newest 200, upgrade to cursor pagination (spec §9) when a workshop gets near it
    private static final PageRequest PAGE = PageRequest.of(0, 200);

    private final LeakAlertRepository alerts;

    ListLeakAlertsUseCase(LeakAlertRepository alerts) {
        this.alerts = alerts;
    }

    @Transactional(readOnly = true)
    public List<AlertView> execute(WorkshopContext ctx, Filter filter) {
        ctx.require(Role.WORKSHOP_OWNER);
        var rows = filter == Filter.RESOLVED
                ? alerts.findByWorkshopIdAndResolvedAtIsNotNullOrderByCreatedAtDesc(ctx.workshopId(), PAGE)
                : alerts.findByWorkshopIdAndResolvedAtIsNullOrderByCreatedAtDesc(ctx.workshopId(), PAGE);
        return rows.stream().map(ListLeakAlertsUseCase::view).toList();
    }

    private static AlertView view(LeakAlert a) {
        return new AlertView(a.getId(), a.getType(), a.getReferenceId(), a.getDetails(), a.getCreatedAt(), a.getResolvedAt(), a.getComment());
    }
}
