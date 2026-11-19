package co.nitida.identity.application;

import co.nitida.identity.domain.Invitation;
import co.nitida.identity.domain.InvitationRepository;
import co.nitida.shared.Emails;
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

    /** The plain token goes out by mail once; only its hash is stored, and the response does not carry it. */
    public record Result(java.util.UUID invitationId, Instant expiresAt) {}

    private final InvitationRepository invitations;
    private final Mailbox mailbox;
    private final AuditService audit;
    private final Clock clock;

    InviteMemberUseCase(InvitationRepository invitations, Mailbox mailbox, AuditService audit, Clock clock) {
        this.invitations = invitations;
        this.mailbox = mailbox;
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
        mailbox.send(invitation.getEmail(), "Invitación a un taller en Nítida",
                "Te invitaron a un taller en Nítida como " + roleInSpanish(c.role())
                        + ". Inicia sesión con este correo y acepta con el código de abajo. Vence en 72 horas.\n\n" + token + "\n");
        audit.record(c.ctx().workshopId(), c.ctx().userId(), "MEMBER_INVITED", "invitation", invitation.getId(),
                "{\"role\":\"" + c.role() + "\"}");
        return new Result(invitation.getId(), invitation.getExpiresAt());
    }

    private static String roleInSpanish(co.nitida.shared.Role role) {
        return switch (role) {
            case MECHANIC -> "mecánico";
            case FRONT_DESK -> "recepción";
            case WORKSHOP_OWNER -> "dueño";
        };
    }
}
