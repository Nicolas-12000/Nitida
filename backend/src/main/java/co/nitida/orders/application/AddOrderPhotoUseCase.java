package co.nitida.orders.application;

import co.nitida.files.FilesApi;
import co.nitida.orders.domain.OrderPhoto;
import co.nitida.shared.ApiException;
import co.nitida.shared.Role;
import co.nitida.shared.UuidV7;
import co.nitida.shared.WorkshopContext;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AddOrderPhotoUseCase {
    public record Command(WorkshopContext ctx, UUID orderId, UUID fileId, OrderPhoto.Moment moment) {}

    private final OrderAccess access;
    private final FilesApi files;
    private final Clock clock;

    AddOrderPhotoUseCase(OrderAccess access, FilesApi files, Clock clock) {
        this.access = access;
        this.files = files;
        this.clock = clock;
    }

    @Transactional
    public void execute(Command c) {
        c.ctx().require(Role.WORKSHOP_OWNER, Role.MECHANIC);
        if (c.fileId() == null || c.moment() == null) throw ApiException.unprocessable("INVALID_PHOTO", "Indique la foto y si es de antes o después");
        files.requireOwned(c.fileId(), c.ctx().workshopId());
        access.load(c.orderId(), c.ctx()).addPhoto(new OrderPhoto(UuidV7.next(), c.fileId(), c.moment()), clock.instant());
    }
}
