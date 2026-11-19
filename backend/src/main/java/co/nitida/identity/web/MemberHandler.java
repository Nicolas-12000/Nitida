package co.nitida.identity.web;

import co.nitida.identity.AuthenticatedUser;
import co.nitida.identity.IdentityApi;
import co.nitida.identity.application.AcceptInvitationUseCase;
import co.nitida.identity.application.InviteMemberUseCase;
import co.nitida.identity.application.RemoveMemberUseCase;
import co.nitida.shared.ApiException;
import co.nitida.shared.Role;
import co.nitida.shared.WorkshopContext;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

@Component
class MemberHandler {
    record InviteRequest(String email, Role role) {}

    private final InviteMemberUseCase invite;
    private final AcceptInvitationUseCase accept;
    private final RemoveMemberUseCase remove;
    private final IdentityApi identity;

    MemberHandler(InviteMemberUseCase invite, AcceptInvitationUseCase accept, RemoveMemberUseCase remove, IdentityApi identity) {
        this.invite = invite;
        this.accept = accept;
        this.remove = remove;
        this.identity = identity;
    }

    ServerResponse invite(ServerRequest req) throws Exception {
        var ctx = WorkshopContext.forPath(req.pathVariable("id"));
        var body = req.body(InviteRequest.class);
        if (body.role() == null) throw ApiException.unprocessable("INVALID_ROLE", "Indique el rol");
        return ServerResponse.status(HttpStatus.CREATED).body(invite.execute(new InviteMemberUseCase.Command(ctx, body.email(), body.role())));
    }

    ServerResponse accept(ServerRequest req) {
        var user = AuthenticatedUser.current();
        return ServerResponse.ok().body(accept.execute(
                new AcceptInvitationUseCase.Command(user.userId(), user.email(), req.pathVariable("token"))));
    }

    ServerResponse list(ServerRequest req) {
        var ctx = WorkshopContext.forPath(req.pathVariable("id")).require(Role.WORKSHOP_OWNER);
        return ServerResponse.ok().body(identity.membersOf(ctx.workshopId()));
    }

    ServerResponse remove(ServerRequest req) {
        remove.execute(new RemoveMemberUseCase.Command(WorkshopContext.forPath(req.pathVariable("id")), UUID.fromString(req.pathVariable("userId"))));
        return ServerResponse.noContent().build();
    }
}
