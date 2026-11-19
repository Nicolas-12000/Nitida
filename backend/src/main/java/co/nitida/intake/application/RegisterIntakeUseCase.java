package co.nitida.intake.application;

import co.nitida.files.FilesApi;
import co.nitida.intake.IntakeRegistered;
import co.nitida.intake.domain.Intake;
import co.nitida.intake.domain.IntakeRepository;
import co.nitida.intake.domain.IntakeStatus;
import co.nitida.motorcycles.MotorcyclesApi;
import co.nitida.motorcycles.MotorcyclesApi.MotorcycleDetails;
import co.nitida.shared.ApiException;
import co.nitida.shared.Mileage;
import co.nitida.shared.Role;
import co.nitida.shared.UuidV7;
import co.nitida.shared.WorkshopContext;
import co.nitida.workshops.WorkshopsApi;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterIntakeUseCase {
    public record Command(WorkshopContext ctx, String plate, String vin, MotorcycleDetails details, int mileage,
                          UUID odometerPhotoId, UUID motorcyclePhotoId) {}

    public record Result(UUID intakeId, UUID motorcycleId, boolean newMotorcycle) {}

    private final IntakeRepository intakes;
    private final MotorcyclesApi motorcycles;
    private final WorkshopsApi workshops;
    private final FilesApi files;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    RegisterIntakeUseCase(IntakeRepository intakes, MotorcyclesApi motorcycles, WorkshopsApi workshops, FilesApi files,
                          ApplicationEventPublisher events, Clock clock) {
        this.intakes = intakes;
        this.motorcycles = motorcycles;
        this.workshops = workshops;
        this.files = files;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public Result execute(Command c) {
        c.ctx().require(Role.WORKSHOP_OWNER, Role.MECHANIC, Role.FRONT_DESK);
        workshops.requireActive(c.ctx().workshopId()); // RN-04
        var mileage = new Mileage(c.mileage());
        if (c.odometerPhotoId() == null || c.motorcyclePhotoId() == null) {
            throw ApiException.unprocessable("PHOTO_REQUIRED", "Se requieren la foto del tablero y la foto de la moto");
        }
        files.requireOwned(c.odometerPhotoId(), c.ctx().workshopId());
        files.requireOwned(c.motorcyclePhotoId(), c.ctx().workshopId());

        var registration = motorcycles.findOrRegister(c.ctx().workshopId(), c.plate(), c.vin(), c.details(), mileage.km());
        UUID motorcycleId = registration.motorcycle().id();
        if (intakes.existsByWorkshopIdAndMotorcycleIdAndStatus(c.ctx().workshopId(), motorcycleId, IntakeStatus.OPEN)) {
            throw ApiException.conflict("INTAKE_ALREADY_OPEN", "Esta moto ya tiene una recepción abierta en el taller");
        }
        var now = clock.instant();
        var intake = intakes.save(Intake.register(UuidV7.next(), c.ctx().workshopId(), motorcycleId, mileage.km(),
                c.odometerPhotoId(), c.motorcyclePhotoId(), c.ctx().userId(), now));
        events.publishEvent(new IntakeRegistered(intake.getId(), c.ctx().workshopId(), motorcycleId, mileage.km(), now));
        return new Result(intake.getId(), motorcycleId, registration.created());
    }
}
