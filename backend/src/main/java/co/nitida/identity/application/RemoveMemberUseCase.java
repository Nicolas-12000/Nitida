package co.nitida.identity.application;

import co.nitida.identity.domain.Membership;
import co.nitida.identity.domain.MembershipRepository;
import co.nitida.shared.ApiException;
import co.nitida.shared.Role;
import co.nitida.shared.WorkshopContext;
import co.nitida.shared.audit.AuditService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RemoveMemberUseCase {
    public record Command(WorkshopContext ctx, UUID memberUserId) {}

    private final MembershipRepository memberships;
    private final AuditService audit;

    RemoveMemberUseCase(MembershipRepository memberships, AuditService audit) {
        this.memberships = memberships;
        this.audit = audit;
    }

    @Transactional
    public void execute(Command c) {
        c.ctx().require(Role.WORKSHOP_OWNER);
        var rows = memberships.findByUserIdAndWorkshopId(c.memberUserId(), c.ctx().workshopId());
        if (rows.isEmpty()) throw ApiException.notFound();
        boolean wasOwner = rows.stream().anyMatch(m -> m.getRole() == Role.WORKSHOP_OWNER && m.isActive());
        // a workshop must never be left without an owner
        if (wasOwner && memberships.countByWorkshopIdAndRoleAndActiveTrue(c.ctx().workshopId(), Role.WORKSHOP_OWNER) <= 1) {
            throw ApiException.conflict("LAST_OWNER", "No se puede retirar al único dueño del taller");
        }
        rows.forEach(Membership::deactivate);
        audit.record(c.ctx().workshopId(), c.ctx().userId(), "MEMBER_REMOVED", "user", c.memberUserId(), null);
    }
}
