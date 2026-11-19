package co.nitida.intake.application;

import co.nitida.intake.domain.DiscardReason;
import co.nitida.intake.domain.IntakeRepository;
import co.nitida.shared.ApiException;
import co.nitida.shared.Role;
import co.nitida.shared.WorkshopContext;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiscardIntakeUseCase {
    public record Command(WorkshopContext ctx, UUID intakeId, DiscardReason reason) {}

    private final IntakeRepository intakes;

    DiscardIntakeUseCase(IntakeRepository intakes) {
        this.intakes = intakes;
    }

    @Transactional
    public void execute(Command c) {
        c.ctx().require(Role.WORKSHOP_OWNER, Role.MECHANIC, Role.FRONT_DESK);
        intakes.findByIdAndWorkshopId(c.intakeId(), c.ctx().workshopId()).orElseThrow(ApiException::notFound).discard(c.reason());
    }
}
