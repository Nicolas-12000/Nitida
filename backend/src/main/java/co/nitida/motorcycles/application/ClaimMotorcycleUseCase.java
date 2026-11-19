package co.nitida.motorcycles.application;

import co.nitida.motorcycles.domain.ClaimCode;
import co.nitida.motorcycles.domain.VehicleOwner;
import co.nitida.motorcycles.domain.VehicleOwnerRepository;
import co.nitida.shared.ApiException;
import co.nitida.shared.UuidV7;
import co.nitida.shared.audit.AuditService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-20: the person who got the delivery receipt claims the motorcycle with its single-use code (RN-80, RN-81). */
@Service
public class ClaimMotorcycleUseCase {
    public record Command(UUID userId, String code) {}

    public record Result(UUID motorcycleId) {}

    private final OwnerCodes codes;
    private final VehicleOwnerRepository owners;
    private final AuditService audit;
    private final Clock clock;

    ClaimMotorcycleUseCase(OwnerCodes codes, VehicleOwnerRepository owners, AuditService audit, Clock clock) {
        this.codes = codes;
        this.owners = owners;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public Result execute(Command c) {
        var code = codes.redeem(c.userId(), c.code(), ClaimCode.DELIVERY);
        UUID moto = code.getMotorcycleId();
        var current = owners.findByMotorcycleIdAndValidToIsNull(moto);
        if (current.isPresent()) {
            if (current.get().getUserId().equals(c.userId())) return new Result(moto);
            // RN-81: a later receipt never takes the motorcycle from its owner; only a transfer does (RN-82)
            throw ApiException.conflict("ALREADY_OWNED", "Esta moto ya tiene propietario: pídale un código de traspaso");
        }
        var owner = owners.save(new VehicleOwner(UuidV7.next(), moto, c.userId(), clock.instant()));
        audit.record(null, c.userId(), "MOTORCYCLE_CLAIMED", "vehicle_owner", owner.getId(), null);
        return new Result(moto);
    }
}
