package co.nitida.workshops.web;

import co.nitida.identity.AuthenticatedUser;
import co.nitida.identity.IdentityApi;
import co.nitida.shared.ApiException;
import co.nitida.shared.Role;
import co.nitida.shared.WorkshopContext;
import co.nitida.workshops.WorkshopStatus;
import co.nitida.workshops.WorkshopsApi;
import co.nitida.workshops.application.ChangeWorkshopStatusUseCase;
import co.nitida.workshops.application.RegisterWorkshopUseCase;
import co.nitida.workshops.application.UpdateWorkshopSettingsUseCase;
import co.nitida.workshops.domain.WorkshopRepository;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

@Component
class WorkshopHandler {
    record RegisterRequest(String name, String documentNumber, String address, String municipality, String phone,
                           UUID facadePhotoId) {}

    record SettingsRequest(int intakeAlertHours, long cashTolerance, int stalledDays, long version) {}

    record StatusRequest(WorkshopStatus status, String evidence) {}

    record MineItem(UUID id, String name, String municipality, WorkshopStatus status, List<Role> roles) {}

    private final RegisterWorkshopUseCase register;
    private final ChangeWorkshopStatusUseCase changeStatus;
    private final UpdateWorkshopSettingsUseCase settings;
    private final WorkshopsApi api;
    private final IdentityApi identity;
    private final WorkshopRepository workshops;

    WorkshopHandler(RegisterWorkshopUseCase register, ChangeWorkshopStatusUseCase changeStatus,
                    UpdateWorkshopSettingsUseCase settings, WorkshopsApi api, IdentityApi identity, WorkshopRepository workshops) {
        this.register = register;
        this.changeStatus = changeStatus;
        this.settings = settings;
        this.api = api;
        this.identity = identity;
        this.workshops = workshops;
    }

    ServerResponse register(ServerRequest req) throws Exception {
        var b = req.body(RegisterRequest.class);
        var result = register.execute(new RegisterWorkshopUseCase.Command(userId(), b.name(), b.documentNumber(),
                b.address(), b.municipality(), b.phone(), b.facadePhotoId()));
        return ServerResponse.status(HttpStatus.CREATED).body(result);
    }

    ServerResponse mine(ServerRequest req) {
        var memberships = identity.membershipsOf(userId());
        var rolesByWorkshop = memberships.stream().collect(Collectors.groupingBy(
                IdentityApi.WorkshopRole::workshopId, Collectors.mapping(IdentityApi.WorkshopRole::role, Collectors.toList())));
        var items = workshops.findByIdIn(rolesByWorkshop.keySet()).stream()
                .map(w -> new MineItem(w.getId(), w.getName(), w.getMunicipality(), w.getStatus(), rolesByWorkshop.get(w.getId())))
                .toList();
        return ServerResponse.ok().body(items);
    }

    ServerResponse detail(ServerRequest req) {
        var ctx = WorkshopContext.forPath(req.pathVariable("id"));
        return ServerResponse.ok().body(api.find(ctx.workshopId()).orElseThrow(ApiException::notFound));
    }

    ServerResponse updateSettings(ServerRequest req) throws Exception {
        var ctx = WorkshopContext.forPath(req.pathVariable("id"));
        var b = req.body(SettingsRequest.class);
        return ServerResponse.ok().body(settings.execute(new UpdateWorkshopSettingsUseCase.Command(
                ctx, b.intakeAlertHours(), b.cashTolerance(), b.stalledDays(), b.version())));
    }

    ServerResponse adminList(ServerRequest req) {
        var status = req.param("status").map(WorkshopStatus::valueOf).orElse(WorkshopStatus.PENDING_VERIFICATION);
        if (!identity.isPlatformAdmin(userId())) throw ApiException.forbidden();
        var items = workshops.findByStatusOrderByCreatedAtAsc(status).stream()
                .map(w -> Map.of("id", w.getId(), "name", w.getName(), "municipality", w.getMunicipality(),
                        "documentNumber", w.getDocumentNumber(), "phone", w.getPhone(), "status", w.getStatus()))
                .toList();
        return ServerResponse.ok().body(items);
    }

    ServerResponse changeStatus(ServerRequest req) throws Exception {
        var b = req.body(StatusRequest.class);
        var status = changeStatus.execute(new ChangeWorkshopStatusUseCase.Command(
                userId(), UUID.fromString(req.pathVariable("id")), b.status(), b.evidence()));
        return ServerResponse.ok().body(Map.of("status", status));
    }

    /** JWKS-like list so third parties can verify signatures on their own (section 8.3). */
    ServerResponse publicKeys(ServerRequest req) {
        var enc = Base64.getUrlEncoder().withoutPadding();
        var keys = api.publicKeys().stream().map(k -> Map.of(
                "kty", "OKP", "crv", "Ed25519", "use", "sig",
                "kid", k.keyId().toString(),
                "workshopId", k.workshopId().toString(),
                "retired", k.retired(),
                // raw 32-byte key = last 32 bytes of the X.509 DER encoding
                "x", enc.encodeToString(java.util.Arrays.copyOfRange(k.publicKey(), k.publicKey().length - 32, k.publicKey().length)))).toList();
        return ServerResponse.ok().body(Map.of("keys", keys));
    }

    private static UUID userId() {
        return AuthenticatedUser.current().userId();
    }
}
