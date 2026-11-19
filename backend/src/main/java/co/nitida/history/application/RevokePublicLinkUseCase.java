package co.nitida.history.application;

import co.nitida.history.domain.PublicLinkRepository;
import co.nitida.shared.ApiException;
import co.nitida.shared.Role;
import co.nitida.shared.WorkshopContext;
import co.nitida.shared.audit.AuditService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RevokePublicLinkUseCase {
    public record Command(WorkshopContext ctx, UUID linkId) {}

    private final PublicLinkRepository links;
    private final AuditService audit;
    private final Clock clock;

    RevokePublicLinkUseCase(PublicLinkRepository links, AuditService audit, Clock clock) {
        this.links = links;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public void execute(Command c) {
        c.ctx().require(Role.WORKSHOP_OWNER, Role.FRONT_DESK);
        var link = links.findById(c.linkId()).filter(l -> c.ctx().workshopId().equals(l.getWorkshopId())).orElseThrow(ApiException::notFound);
        link.revoke(clock.instant()); // idempotent
        audit.record(c.ctx().workshopId(), c.ctx().userId(), "PUBLIC_LINK_REVOKED", "public_link", link.getId(), null);
    }
}
