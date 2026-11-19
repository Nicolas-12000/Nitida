package co.nitida.intake.web;

import co.nitida.intake.IntakeApi;
import co.nitida.intake.application.ChangeMotorcyclePlateUseCase;
import co.nitida.intake.application.DiscardIntakeUseCase;
import co.nitida.intake.application.RegisterIntakeUseCase;
import co.nitida.intake.domain.DiscardReason;
import co.nitida.motorcycles.MotorcyclesApi;
import co.nitida.motorcycles.MotorcyclesApi.MotorcycleDetails;
import co.nitida.shared.WorkshopContext;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

@Component
class IntakeHandler {
    record RegisterRequest(String plate, String vin, String brand, String line, Integer modelYear, Integer displacementCc,
                           int mileage, UUID odometerPhotoId, UUID motorcyclePhotoId) {}

    record DiscardRequest(DiscardReason reason) {}

    record PlateRequest(String plate) {}

    record IntakeView(UUID id, UUID motorcycleId, String plate, String brand, String line, int mileage, Instant createdAt) {}

    private final RegisterIntakeUseCase register;
    private final DiscardIntakeUseCase discard;
    private final ChangeMotorcyclePlateUseCase changePlate;
    private final IntakeApi intakes;
    private final MotorcyclesApi motorcycles;

    IntakeHandler(RegisterIntakeUseCase register, DiscardIntakeUseCase discard, ChangeMotorcyclePlateUseCase changePlate,
                  IntakeApi intakes, MotorcyclesApi motorcycles) {
        this.register = register;
        this.discard = discard;
        this.changePlate = changePlate;
        this.intakes = intakes;
        this.motorcycles = motorcycles;
    }

    ServerResponse register(ServerRequest req) throws Exception {
        var b = req.body(RegisterRequest.class);
        var details = b.brand() == null && b.line() == null ? null
                : new MotorcycleDetails(b.brand(), b.line(), b.modelYear(), b.displacementCc());
        var result = register.execute(new RegisterIntakeUseCase.Command(WorkshopContext.current(), b.plate(), b.vin(),
                details, b.mileage(), b.odometerPhotoId(), b.motorcyclePhotoId()));
        return ServerResponse.status(HttpStatus.CREATED).body(result);
    }

    ServerResponse list(ServerRequest req) {
        var ctx = WorkshopContext.current();
        var open = intakes.open(ctx.workshopId());
        var motos = motorcycles.findAll(open.stream().map(IntakeApi.IntakeInfo::motorcycleId).distinct().toList());
        var views = open.stream().map(i -> {
            var m = motos.get(i.motorcycleId());
            return new IntakeView(i.id(), i.motorcycleId(), m.plate(), m.brand(), m.line(), i.mileage(), i.createdAt());
        }).toList();
        return ServerResponse.ok().body(views);
    }

    ServerResponse discard(ServerRequest req) throws Exception {
        var b = req.body(DiscardRequest.class);
        discard.execute(new DiscardIntakeUseCase.Command(WorkshopContext.current(), UUID.fromString(req.pathVariable("id")), b.reason()));
        return ServerResponse.noContent().build();
    }

    ServerResponse changePlate(ServerRequest req) throws Exception {
        var b = req.body(PlateRequest.class);
        changePlate.execute(new ChangeMotorcyclePlateUseCase.Command(WorkshopContext.current(), UUID.fromString(req.pathVariable("id")), b.plate()));
        return ServerResponse.noContent().build();
    }
}
