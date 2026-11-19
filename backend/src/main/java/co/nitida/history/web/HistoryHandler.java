package co.nitida.history.web;

import co.nitida.history.application.CreateAmendmentUseCase;
import co.nitida.history.application.CreatePublicLinkUseCase;
import co.nitida.history.application.DeclareOdometerChangeUseCase;
import co.nitida.history.application.RevokePublicLinkUseCase;
import co.nitida.history.application.ViewHistoryUseCases;
import co.nitida.identity.AuthenticatedUser;
import co.nitida.shared.WorkshopContext;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

@Component
class HistoryHandler {
    record AmendmentRequest(long amendsSeq, String reason, String diagnosis, Integer mileage) {}

    record OdometerRequest(int mileage, String reason, UUID photoId) {}

    record LinkRequest(Integer days) {}

    private final CreateAmendmentUseCase amend;
    private final DeclareOdometerChangeUseCase odometer;
    private final CreatePublicLinkUseCase createLink;
    private final RevokePublicLinkUseCase revokeLink;
    private final ViewHistoryUseCases view;

    HistoryHandler(CreateAmendmentUseCase amend, DeclareOdometerChangeUseCase odometer, CreatePublicLinkUseCase createLink,
                   RevokePublicLinkUseCase revokeLink, ViewHistoryUseCases view) {
        this.amend = amend;
        this.odometer = odometer;
        this.createLink = createLink;
        this.revokeLink = revokeLink;
        this.view = view;
    }

    ServerResponse view(ServerRequest req) {
        return ServerResponse.ok().body(view.viewAsWorkshop(new ViewHistoryUseCases.WorkshopCommand(
                WorkshopContext.current(), UUID.fromString(req.pathVariable("id")))));
    }

    ServerResponse amend(ServerRequest req) throws Exception {
        var b = req.body(AmendmentRequest.class);
        return ServerResponse.status(HttpStatus.CREATED).body(amend.execute(new CreateAmendmentUseCase.Command(
                WorkshopContext.current(), UUID.fromString(req.pathVariable("id")), b.amendsSeq(), b.reason(), b.diagnosis(), b.mileage())));
    }

    ServerResponse odometerChange(ServerRequest req) throws Exception {
        var b = req.body(OdometerRequest.class);
        return ServerResponse.status(HttpStatus.CREATED).body(odometer.execute(new DeclareOdometerChangeUseCase.Command(
                WorkshopContext.current(), UUID.fromString(req.pathVariable("id")), b.mileage(), b.reason(), b.photoId())));
    }

    ServerResponse createLink(ServerRequest req) throws Exception {
        var days = req.body(LinkRequest.class).days();
        return ServerResponse.status(HttpStatus.CREATED).body(createLink.execute(new CreatePublicLinkUseCase.Command(
                WorkshopContext.current(), UUID.fromString(req.pathVariable("id")), days)));
    }

    ServerResponse viewAsOwner(ServerRequest req) {
        return ServerResponse.ok().body(view.viewAsOwner(new ViewHistoryUseCases.OwnerCommand(
                AuthenticatedUser.current().userId(), UUID.fromString(req.pathVariable("id")))));
    }

    ServerResponse createOwnerLink(ServerRequest req) throws Exception {
        var days = req.body(LinkRequest.class).days();
        return ServerResponse.status(HttpStatus.CREATED).body(createLink.execute(new CreatePublicLinkUseCase.OwnerCommand(
                AuthenticatedUser.current().userId(), UUID.fromString(req.pathVariable("id")), days)));
    }

    ServerResponse revokeLink(ServerRequest req) {
        revokeLink.execute(new RevokePublicLinkUseCase.Command(WorkshopContext.current(), UUID.fromString(req.pathVariable("id"))));
        return ServerResponse.noContent().build();
    }

    /** No authentication; never indexed or cached (RN-74). */
    ServerResponse viewPublic(ServerRequest req) {
        var result = view.viewPublic(new ViewHistoryUseCases.PublicCommand(req.pathVariable("token"), req.servletRequest().getRemoteAddr()));
        return ServerResponse.ok().header("X-Robots-Tag", "noindex, nofollow").cacheControl(CacheControl.noStore()).body(result);
    }
}
