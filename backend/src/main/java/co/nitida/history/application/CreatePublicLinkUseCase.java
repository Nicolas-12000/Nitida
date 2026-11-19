package co.nitida.history.application;

import co.nitida.history.domain.PublicLink;
import co.nitida.history.domain.PublicLinkRepository;
import co.nitida.intake.IntakeApi;
import co.nitida.motorcycles.MotorcyclesApi;
import co.nitida.shared.ApiException;
import co.nitida.shared.Role;
import co.nitida.shared.UuidV7;
import co.nitida.shared.WorkshopContext;
import co.nitida.shared.audit.AuditService;
import co.nitida.shared.crypto.SecureTokens;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-15 / RN-70..72: random 128-bit token shown once; only its SHA-256 is stored. */
@Service
public class CreatePublicLinkUseCase {
    public record Command(WorkshopContext ctx, UUID motorcycleId, Integer days) {}

    public record OwnerCommand(UUID userId, UUID motorcycleId, Integer days) {}

    public record Result(UUID linkId, String token, Instant expiresAt) {}

    private final PublicLinkRepository links;
    private final IntakeApi intakes;
    private final MotorcyclesApi motorcycles;
    private final AuditService audit;
    private final Clock clock;

    CreatePublicLinkUseCase(PublicLinkRepository links, IntakeApi intakes, MotorcyclesApi motorcycles, AuditService audit, Clock clock) {
        this.links = links;
        this.intakes = intakes;
        this.motorcycles = motorcycles;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public Result execute(Command c) {
        c.ctx().require(Role.WORKSHOP_OWNER, Role.FRONT_DESK);
        if (!intakes.hasReceived(c.ctx().workshopId(), c.motorcycleId())) throw ApiException.notFound();
        return create(c.motorcycleId(), c.ctx().workshopId(), c.ctx().userId(), c.days());
    }

    /** RN-70: the active owner can share their own motorcycle too (the link has no workshop). */
    @Transactional
    public Result execute(OwnerCommand c) {
        motorcycles.ownership(c.userId(), c.motorcycleId()).filter(MotorcyclesApi.OwnershipPeriod::active).orElseThrow(ApiException::notFound);
        return create(c.motorcycleId(), null, c.userId(), c.days());
    }

    private Result create(UUID motorcycleId, UUID workshopId, UUID userId, Integer days) {
        String token = SecureTokens.newToken();
        var link = links.save(new PublicLink(UuidV7.next(), motorcycleId, workshopId, userId, SecureTokens.hash(token), days, clock.instant()));
        audit.record(workshopId, userId, "PUBLIC_LINK_CREATED", "public_link", link.getId(), null);
        return new Result(link.getId(), token, link.getExpiresAt());
    }
}
