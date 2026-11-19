package co.nitida.orders.application;

import co.nitida.catalog.CatalogApi;
import co.nitida.shared.Role;
import co.nitida.shared.WorkshopContext;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RemovePartUseCase {
    public record Command(WorkshopContext ctx, UUID orderId, UUID partId) {}

    private final OrderAccess access;
    private final CatalogApi catalog;
    private final Clock clock;

    RemovePartUseCase(OrderAccess access, CatalogApi catalog, Clock clock) {
        this.access = access;
        this.catalog = catalog;
        this.clock = clock;
    }

    @Transactional
    public void execute(Command c) {
        c.ctx().require(Role.WORKSHOP_OWNER, Role.MECHANIC);
        var order = access.load(c.orderId(), c.ctx());
        var removed = order.removePart(c.partId(), clock.instant());
        if (removed.getSerialId() != null) catalog.releaseSerial(removed.getSerialId(), order.getId());
    }
}
