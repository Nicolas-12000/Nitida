package co.nitida.cashdesk.application;

import co.nitida.cashdesk.domain.AlertType;
import co.nitida.cashdesk.domain.BogotaDay;
import co.nitida.cashdesk.domain.CashCount;
import co.nitida.cashdesk.domain.CashCountRepository;
import co.nitida.cashdesk.domain.CashCountStatus;
import co.nitida.orders.OrdersApi;
import co.nitida.shared.ApiException;
import co.nitida.shared.Role;
import co.nitida.shared.UuidV7;
import co.nitida.shared.WorkshopContext;
import co.nitida.shared.audit.AuditService;
import co.nitida.workshops.WorkshopsApi;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-17: closes the day; a difference beyond the workshop tolerance raises F3 (RN-52). */
@Service
public class CloseCashCountUseCase {
    public record Command(WorkshopContext ctx, LocalDate day, Long counted) {}

    private final CashCountRepository counts;
    private final OrdersApi orders;
    private final WorkshopsApi workshops;
    private final LeakAlerts alerts;
    private final AuditService audit;
    private final Clock clock;

    CloseCashCountUseCase(CashCountRepository counts, OrdersApi orders, WorkshopsApi workshops, LeakAlerts alerts,
                          AuditService audit, Clock clock) {
        this.counts = counts;
        this.orders = orders;
        this.workshops = workshops;
        this.alerts = alerts;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public CashCountView execute(Command c) {
        var ctx = c.ctx().require(Role.WORKSHOP_OWNER);
        CashDays.requireNotFuture(c.day(), clock);
        if (c.counted() == null) throw ApiException.unprocessable("COUNTED_REQUIRED", "Indique el efectivo contado");
        long tolerance = workshops.requireActive(ctx.workshopId()).cashTolerance();
        long expected = orders.cashTotal(ctx.workshopId(), BogotaDay.start(c.day()), BogotaDay.end(c.day()));
        var now = clock.instant();
        var count = counts.findByWorkshopIdAndCountDay(ctx.workshopId(), c.day())
                .map(existing -> {
                    existing.recount(expected, c.counted(), tolerance, ctx.userId(), now);
                    return existing;
                })
                .orElseGet(() -> CashCount.close(UuidV7.next(), ctx.workshopId(), c.day(), expected, c.counted(), tolerance, ctx.userId(), now));
        counts.save(count);
        if (count.getStatus() == CashCountStatus.UNBALANCED) {
            alerts.raise(ctx.workshopId(), AlertType.F3_CASH_MISMATCH, count.getId(), c.day() + ":" + now.toEpochMilli(),
                    "Cuadre del " + c.day() + " descuadrado por " + count.getDifference() + " pesos");
        }
        audit.record(ctx.workshopId(), ctx.userId(), "CASH_COUNT_CLOSED", "cash_count", count.getId(),
                "{\"status\":\"" + count.getStatus() + "\",\"difference\":" + count.getDifference() + "}");
        return CashCountView.of(count);
    }
}
