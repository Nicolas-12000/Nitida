package co.nitida.orders.application;

import co.nitida.orders.domain.Job;
import co.nitida.orders.domain.JobType;
import co.nitida.shared.Role;
import co.nitida.shared.UuidV7;
import co.nitida.shared.WorkshopContext;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-08: diagnosis, labor, warranty and the list of jobs (replaced as a whole). Parts have their own use cases. */
@Service
public class EditWorkOrderUseCase {
    public record JobInput(JobType type, String description) {}

    public record Command(WorkshopContext ctx, UUID orderId, long version, String diagnosis, Long laborCost,
                          Integer warrantyDays, Integer warrantyKm, List<JobInput> jobs) {}

    private final OrderAccess access;
    private final Clock clock;

    EditWorkOrderUseCase(OrderAccess access, Clock clock) {
        this.access = access;
        this.clock = clock;
    }

    @Transactional
    public void execute(Command c) {
        c.ctx().require(Role.WORKSHOP_OWNER, Role.MECHANIC);
        var order = access.load(c.orderId(), c.ctx());
        order.checkVersion(c.version());
        var jobs = c.jobs() == null ? null : c.jobs().stream().map(j -> new Job(UuidV7.next(), j.type(), j.description())).toList();
        order.edit(clock.instant(), c.diagnosis(), c.laborCost(), c.warrantyDays(), c.warrantyKm(), jobs);
    }
}
