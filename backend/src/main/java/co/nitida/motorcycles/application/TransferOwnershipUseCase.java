package co.nitida.motorcycles.application;

import co.nitida.motorcycles.domain.ClaimCode;
import co.nitida.motorcycles.domain.ClaimCodeRepository;
import co.nitida.motorcycles.domain.VehicleOwnerRepository;
import co.nitida.shared.ApiException;
import co.nitida.shared.UuidV7;
import co.nitida.shared.audit.AuditService;
import co.nitida.shared.crypto.SecureTokens;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-21 (step 1): the active owner gets a single-use transfer code to give to the buyer (RN-82). */
@Service
public class TransferOwnershipUseCase {
    static final Duration VALIDITY = Duration.ofDays(7);

    public record Command(UUID userId, UUID motorcycleId) {}

    /** The code is returned in clear exactly once; only its hash is stored. */
    public record Result(String code, Instant expiresAt) {}

    private final VehicleOwnerRepository owners;
    private final ClaimCodeRepository codes;
    private final AuditService audit;
    private final Clock clock;

    TransferOwnershipUseCase(VehicleOwnerRepository owners, ClaimCodeRepository codes, AuditService audit, Clock clock) {
        this.owners = owners;
        this.codes = codes;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public Result execute(Command c) {
        owners.findByMotorcycleIdAndValidToIsNull(c.motorcycleId()).filter(o -> o.getUserId().equals(c.userId()))
                .orElseThrow(ApiException::notFound);
        String code = SecureTokens.newCode(8);
        Instant expires = clock.instant().plus(VALIDITY);
        var saved = codes.save(new ClaimCode(UuidV7.next(), c.motorcycleId(), ClaimCode.TRANSFER, SecureTokens.hash(code), c.userId(), expires));
        audit.record(null, c.userId(), "OWNERSHIP_TRANSFER_STARTED", "claim_code", saved.getId(), null);
        return new Result(code, expires);
    }
}
