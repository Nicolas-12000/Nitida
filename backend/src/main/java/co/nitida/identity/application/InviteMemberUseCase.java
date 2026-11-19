package co.nitida.identity.application;

import co.nitida.identity.domain.Invitation;
import co.nitida.identity.domain.InvitationRepository;
import co.nitida.shared.Role;
import co.nitida.shared.UuidV7;
import co.nitida.shared.WorkshopContext;
import co.nitida.shared.audit.AuditService;
import co.nitida.shared.crypto.SecureTokens;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InviteMemberUseCase {
    public record Command(WorkshopContext ctx, String email, Role role) {}

    /** The plain token is returned once; only its hash is stored (RN-71 style). */
    public record Result(java.util.UUID invitationId, String token, Instant expiresAt) {}

    private final InvitationRepository invitations;
    private final AuditService audit;
    private final Clock clock;

    InviteMemberUseCase(InvitationRepository invitations, AuditService audit, Clock clock) {
        this.invitations = invitations;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public Result execute(Command c) {
        c.ctx().require(Role.WORKSHOP_OWNER);
        String token = SecureTokens.newToken();
        var invitation = new Invitation(UuidV7.next(), c.ctx().workshopId(), Emails.normalize(c.email()),
                c.role(), SecureTokens.hash(token), c.ctx().userId(), clock.instant());
        invitations.save(invitation);
        audit.record(c.ctx().workshopId(), c.ctx().userId(), "MEMBER_INVITED", "invitation", invitation.getId(),
                "{\"role\":\"" + c.role() + "\"}");
        return new Result(invitation.getId(), token, invitation.getExpiresAt());
    }
}
