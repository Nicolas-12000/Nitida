package co.nitida.cashdesk.application;

import co.nitida.cashdesk.domain.BogotaDay;
import co.nitida.cashdesk.domain.CashCountRepository;
import co.nitida.cashdesk.domain.CashCountStatus;
import co.nitida.orders.OrdersApi;
import co.nitida.shared.Role;
import co.nitida.shared.WorkshopContext;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-17 (read): the stored count once closed; otherwise expected = Σ non-voided cash of the Bogota day (RN-52). */
@Service
public class GetCashCountUseCase {
    private final CashCountRepository counts;
    private final OrdersApi orders;
    private final Clock clock;

    GetCashCountUseCase(CashCountRepository counts, OrdersApi orders, Clock clock) {
        this.counts = counts;
        this.orders = orders;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CashCountView execute(WorkshopContext ctx, LocalDate day) {
        ctx.require(Role.WORKSHOP_OWNER);
        CashDays.requireNotFuture(day, clock);
        var stored = counts.findByWorkshopIdAndCountDay(ctx.workshopId(), day);
        if (stored.isPresent() && stored.get().getStatus() != CashCountStatus.OPEN) return CashCountView.of(stored.get());
        long expected = orders.cashTotal(ctx.workshopId(), BogotaDay.start(day), BogotaDay.end(day));
        return CashCountView.open(day, expected, stored.map(c -> c.getReopenReason()).orElse(null));
    }
}
