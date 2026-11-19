package co.nitida.motorcycles.application;

import co.nitida.motorcycles.OwnershipTransferred;
import co.nitida.motorcycles.domain.ClaimCode;
import co.nitida.motorcycles.domain.VehicleOwner;
import co.nitida.motorcycles.domain.VehicleOwnerRepository;
import co.nitida.shared.ApiException;
import co.nitida.shared.UuidV7;
import co.nitida.shared.audit.AuditService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-21 (step 2): the buyer redeems the code; the seller's ownership closes and history gets OWNERSHIP_TRANSFER (RN-82). */
@Service
public class AcceptOwnershipTransferUseCase {
    public record Command(UUID userId, String code) {}

    public record Result(UUID motorcycleId) {}

    private final OwnerCodes codes;
    private final VehicleOwnerRepository owners;
    private final ApplicationEventPublisher events;
    private final AuditService audit;
    private final Clock clock;

    AcceptOwnershipTransferUseCase(OwnerCodes codes, VehicleOwnerRepository owners, ApplicationEventPublisher events,
                                   AuditService audit, Clock clock) {
        this.codes = codes;
        this.owners = owners;
        this.events = events;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public Result execute(Command c) {
        var code = codes.redeem(c.userId(), c.code(), ClaimCode.TRANSFER);
        UUID moto = code.getMotorcycleId();
        // a code issued by someone who no longer owns the motorcycle is as dead as an unknown one
        var seller = owners.findByMotorcycleIdAndValidToIsNull(moto).filter(o -> o.getUserId().equals(code.getCreatedBy()))
                .orElseThrow(ApiException::notFound);
        if (seller.getUserId().equals(c.userId())) {
            throw ApiException.unprocessable("SAME_OWNER", "Usted ya es el propietario: entregue el código a quien compra la moto");
        }
        var now = clock.instant();
        seller.close(now);
        owners.saveAndFlush(seller); // free the one-active-owner slot before inserting the buyer (RN-81)
        var buyer = owners.save(new VehicleOwner(UuidV7.next(), moto, c.userId(), now));
        events.publishEvent(new OwnershipTransferred(moto, code.getId(), now));
        audit.record(null, c.userId(), "OWNERSHIP_TRANSFER_ACCEPTED", "vehicle_owner", buyer.getId(), null);
        return new Result(moto);
    }
}
