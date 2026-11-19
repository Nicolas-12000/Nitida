package co.nitida.cashdesk.application;

import co.nitida.cashdesk.domain.CashCountRepository;
import co.nitida.shared.ApiException;
import co.nitida.shared.Role;
import co.nitida.shared.WorkshopContext;
import co.nitida.shared.audit.AuditService;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-17: a closed count is never edited; it is reopened with an audited reason. */
@Service
public class ReopenCashCountUseCase {
    public record Command(WorkshopContext ctx, LocalDate day, String reason) {}

    private final CashCountRepository counts;
    private final GetCashCountUseCase get;
    private final AuditService audit;
    private final Clock clock;

    ReopenCashCountUseCase(CashCountRepository counts, GetCashCountUseCase get, AuditService audit, Clock clock) {
        this.counts = counts;
        this.get = get;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public CashCountView execute(Command c) {
        var ctx = c.ctx().require(Role.WORKSHOP_OWNER);
        var count = counts.findByWorkshopIdAndCountDay(ctx.workshopId(), c.day())
                .orElseThrow(() -> ApiException.conflict("CASH_COUNT_NOT_CLOSED", "El cuadre del día ya está abierto"))
                .reopen(c.reason(), clock.instant());
        // the reason may name people: it stays in the count row, not in the audit details
        audit.record(ctx.workshopId(), ctx.userId(), "CASH_COUNT_REOPENED", "cash_count", count.getId(), null);
        return get.execute(ctx, c.day());
    }
}
