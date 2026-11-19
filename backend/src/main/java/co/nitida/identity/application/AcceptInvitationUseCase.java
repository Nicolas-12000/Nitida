package co.nitida.identity.application;

import co.nitida.identity.domain.InvitationRepository;
import co.nitida.shared.ApiException;
import co.nitida.shared.audit.AuditService;
import co.nitida.shared.crypto.SecureTokens;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AcceptInvitationUseCase {
    public record Command(UUID userId, String userEmail, String token) {}

    public record Result(UUID workshopId) {}

    private final InvitationRepository invitations;
    private final IdentityApiImpl identity;
    private final AuditService audit;
    private final Clock clock;

    AcceptInvitationUseCase(InvitationRepository invitations, IdentityApiImpl identity, AuditService audit, Clock clock) {
        this.invitations = invitations;
        this.identity = identity;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public Result execute(Command c) {
        var invitation = invitations.findByTokenHash(SecureTokens.hash(c.token() == null ? "" : c.token()))
                .orElseThrow(ApiException::notFound);
        invitation.accept(c.userEmail(), clock.instant());
        identity.grant(c.userId(), invitation.getWorkshopId(), invitation.getRole());
        audit.record(invitation.getWorkshopId(), c.userId(), "INVITATION_ACCEPTED", "invitation", null, null);
        return new Result(invitation.getWorkshopId());
    }
}
