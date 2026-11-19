package co.nitida.intake.application;

import co.nitida.intake.domain.IntakeRepository;
import co.nitida.motorcycles.MotorcyclesApi;
import co.nitida.shared.ApiException;
import co.nitida.shared.Role;
import co.nitida.shared.WorkshopContext;
import co.nitida.shared.audit.AuditService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RN-14: a workshop that has received the motorcycle corrects its plate. Lives in intake, which knows who attended what. */
@Service
public class ChangeMotorcyclePlateUseCase {
    public record Command(WorkshopContext ctx, UUID motorcycleId, String plate) {}

    private final IntakeRepository intakes;
    private final MotorcyclesApi motorcycles;
    private final AuditService audit;

    ChangeMotorcyclePlateUseCase(IntakeRepository intakes, MotorcyclesApi motorcycles, AuditService audit) {
        this.intakes = intakes;
        this.motorcycles = motorcycles;
        this.audit = audit;
    }

    @Transactional
    public void execute(Command c) {
        var ctx = c.ctx().require(Role.WORKSHOP_OWNER, Role.FRONT_DESK);
        if (!intakes.existsByWorkshopIdAndMotorcycleId(ctx.workshopId(), c.motorcycleId())) throw ApiException.notFound();
        motorcycles.changePlate(c.motorcycleId(), c.plate());
        audit.record(ctx.workshopId(), ctx.userId(), "PLATE_CHANGED", "motorcycle", c.motorcycleId(), null); // the plates stay out of the audit trail
    }
}
