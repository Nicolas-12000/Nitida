package co.nitida.workshops.application;

import co.nitida.shared.ApiException;
import co.nitida.shared.Role;
import co.nitida.shared.WorkshopContext;
import co.nitida.shared.audit.AuditService;
import co.nitida.workshops.WorkshopsApi.WorkshopInfo;
import co.nitida.workshops.domain.WorkshopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateWorkshopSettingsUseCase {
    public record Command(WorkshopContext ctx, int intakeAlertHours, long cashTolerance, int stalledDays, long version) {}

    private final WorkshopRepository workshops;
    private final AuditService audit;

    UpdateWorkshopSettingsUseCase(WorkshopRepository workshops, AuditService audit) {
        this.workshops = workshops;
        this.audit = audit;
    }

    @Transactional
    public WorkshopInfo execute(Command c) {
        c.ctx().require(Role.WORKSHOP_OWNER);
        var w = workshops.findById(c.ctx().workshopId()).orElseThrow(ApiException::notFound);
        if (w.getVersion() != c.version()) {
            throw ApiException.conflict("VERSION_CONFLICT", "Otra persona modificó el taller, recargue e intente de nuevo");
        }
        w.updateSettings(c.intakeAlertHours(), c.cashTolerance(), c.stalledDays());
        workshops.saveAndFlush(w);
        audit.record(w.getId(), c.ctx().userId(), "WORKSHOP_SETTINGS_UPDATED", "workshop", w.getId(), null);
        return WorkshopsApiImpl.info(w);
    }
}
