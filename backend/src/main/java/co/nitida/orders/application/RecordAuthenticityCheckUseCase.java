package co.nitida.orders.application;

import co.nitida.files.FilesApi;
import co.nitida.shared.Role;
import co.nitida.shared.WorkshopContext;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-24: the mechanic checked the manufacturer's seal; photo and method are mandatory (RN-91, RN-95). */
@Service
public class RecordAuthenticityCheckUseCase {
    public record Command(WorkshopContext ctx, UUID orderId, UUID partId, UUID photoFileId, String method) {}

    private final OrderAccess access;
    private final FilesApi files;
    private final Clock clock;

    RecordAuthenticityCheckUseCase(OrderAccess access, FilesApi files, Clock clock) {
        this.access = access;
        this.files = files;
        this.clock = clock;
    }

    @Transactional
    public String execute(Command c) {
        c.ctx().require(Role.WORKSHOP_OWNER, Role.MECHANIC);
        var order = access.load(c.orderId(), c.ctx());
        var part = order.findPart(c.partId());
        if (c.photoFileId() != null) files.requireOwned(c.photoFileId(), c.ctx().workshopId());
        part.attachAuthenticity(c.photoFileId(), c.method());
        order.markChanged(clock.instant());
        return part.getEvidenceLevel().name();
    }
}
