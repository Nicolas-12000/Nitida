package co.nitida.history.application;

import co.nitida.history.domain.EntryContent;
import co.nitida.history.domain.EntryType;
import co.nitida.history.domain.HistoryEntryRepository;
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

/** UC-13 / RN-62: corrections never edit or delete; they add an AMENDMENT that references the corrected entry. */
@Service
public class CreateAmendmentUseCase {
    public record Command(WorkshopContext ctx, UUID motorcycleId, long amendsSeq, String reason, String diagnosis, Integer mileage) {}

    public record Result(long seq) {}

    private final HistoryEntryRepository entries;
    private final HistoryAppender appender;
    private final WorkshopsApi workshops;
    private final Clock clock;

    CreateAmendmentUseCase(HistoryEntryRepository entries, HistoryAppender appender, WorkshopsApi workshops, Clock clock) {
        this.entries = entries;
        this.appender = appender;
        this.workshops = workshops;
        this.clock = clock;
    }

    @Transactional
    public Result execute(Command c) {
        c.ctx().require(Role.WORKSHOP_OWNER, Role.MECHANIC);
        var workshop = workshops.requireActive(c.ctx().workshopId());
        String reason = Texts.requirePublic(c.reason(), 10, 500, "INVALID_REASON", "El motivo debe tener entre 10 y 500 caracteres");
        String diagnosis = c.diagnosis() == null ? null
                : Texts.requirePublic(c.diagnosis(), 10, 2000, "INVALID_DIAGNOSIS", "El diagnóstico debe tener entre 10 y 2000 caracteres");
        Integer mileage = c.mileage() == null ? null : new Mileage(c.mileage()).km();
        if (diagnosis == null && mileage == null) {
            throw ApiException.unprocessable("NOTHING_TO_AMEND", "Indique al menos un campo a corregir");
        }
        var original = entries.findByMotorcycleIdAndSeq(c.motorcycleId(), c.amendsSeq()).orElseThrow(ApiException::notFound);
        // Only the workshop that wrote the entry may amend it; to everyone else it does not exist.
        if (!original.getWorkshopId().equals(c.ctx().workshopId())) throw ApiException.notFound();
        if (original.getType() == EntryType.AMENDMENT) {
            throw ApiException.unprocessable("CANNOT_AMEND_AMENDMENT", "Una enmienda no se puede enmendar: corrija la entrada original");
        }
        if (!c.ctx().has(Role.WORKSHOP_OWNER)) { // mechanics: only their own work
            var author = EntryContent.parse(original.getPrivateContent()).get("mechanicId");
            if (!c.ctx().userId().toString().equals(author)) throw ApiException.forbidden();
        }
        var entry = appender.append(c.motorcycleId(), EntryType.AMENDMENT, c.ctx().workshopId(), null,
                EntryContent.amendment(c.motorcycleId(), c.ctx().workshopId(), workshop.verified(), clock.instant(),
                        c.ctx().userId(), c.amendsSeq(), reason, diagnosis, mileage));
        return new Result(entry.getSeq());
    }
}
