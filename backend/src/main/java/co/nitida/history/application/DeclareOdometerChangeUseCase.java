package co.nitida.history.application;

import co.nitida.files.FilesApi;
import co.nitida.history.domain.EntryContent;
import co.nitida.history.domain.EntryType;
import co.nitida.intake.IntakeApi;
import co.nitida.shared.ApiException;
import co.nitida.shared.Mileage;
import co.nitida.shared.Role;
import co.nitida.shared.Texts;
import co.nitida.shared.WorkshopContext;
import co.nitida.workshops.WorkshopsApi;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-14 / RN-63: declares a replaced or repaired dashboard; it resets the reference mileage. Reason and photo required. */
@Service
public class DeclareOdometerChangeUseCase {
    public record Command(WorkshopContext ctx, UUID motorcycleId, int newMileage, String reason, UUID photoFileId) {}

    public record Result(long seq) {}

    private final HistoryAppender appender;
    private final WorkshopsApi workshops;
    private final IntakeApi intakes;
    private final FilesApi files;
    private final Clock clock;

    DeclareOdometerChangeUseCase(HistoryAppender appender, WorkshopsApi workshops, IntakeApi intakes, FilesApi files, Clock clock) {
        this.appender = appender;
        this.workshops = workshops;
        this.intakes = intakes;
        this.files = files;
        this.clock = clock;
    }

    @Transactional
    public Result execute(Command c) {
        c.ctx().require(Role.WORKSHOP_OWNER, Role.MECHANIC);
        var workshop = workshops.requireActive(c.ctx().workshopId());
        if (!intakes.hasReceived(c.ctx().workshopId(), c.motorcycleId())) throw ApiException.notFound();
        var mileage = new Mileage(c.newMileage());
        String reason = Texts.requirePublic(c.reason(), 10, 500, "INVALID_REASON", "El motivo debe tener entre 10 y 500 caracteres");
        if (c.photoFileId() == null) throw ApiException.unprocessable("PHOTO_REQUIRED", "Se requiere la foto del nuevo tablero");
        String photoHash = files.sha256(c.photoFileId(), c.ctx().workshopId());
        var entry = appender.append(c.motorcycleId(), EntryType.ODOMETER_CHANGE, c.ctx().workshopId(), null,
                EntryContent.odometerChange(c.motorcycleId(), c.ctx().workshopId(), workshop.verified(), clock.instant(),
                        c.ctx().userId(), mileage.km(), reason, photoHash));
        return new Result(entry.getSeq());
    }
}
