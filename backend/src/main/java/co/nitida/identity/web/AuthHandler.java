package co.nitida.identity.web;

import co.nitida.identity.AuthenticatedUser;
import co.nitida.identity.IdentityApi;
import co.nitida.identity.application.ConfirmTotpUseCase;
import co.nitida.identity.application.LoginUseCase;
import co.nitida.identity.application.RegisterUserUseCase;
import co.nitida.identity.application.RequestPasswordResetUseCase;
import co.nitida.identity.application.ResetPasswordUseCase;
import co.nitida.identity.application.SetupTotpUseCase;
import co.nitida.shared.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/** Thin: HTTP in, use case, HTTP out. */
@Component
class AuthHandler {
    record RegisterRequest(String email, String password, String displayName) {}

    record LoginRequest(String email, String password, String totp) {}

    record CodeRequest(String code) {}

    record EmailRequest(String email) {}

    record ResetPasswordRequest(String token, String password) {}

    record MeResponse(UUID id, String email, String displayName, boolean platformAdmin, boolean totpEnabled,
                      boolean totpSetupRequired, List<IdentityApi.WorkshopRole> memberships) {}

    private final RegisterUserUseCase register;
    private final LoginUseCase login;
    private final IdentityApi identity;

    private final SetupTotpUseCase setupTotp;
    private final ConfirmTotpUseCase confirmTotp;
    private final RequestPasswordResetUseCase requestReset;
    private final ResetPasswordUseCase resetPassword;
    private final boolean totpRequired;

    AuthHandler(RegisterUserUseCase register, LoginUseCase login, IdentityApi identity, SetupTotpUseCase setupTotp,
                ConfirmTotpUseCase confirmTotp, RequestPasswordResetUseCase requestReset, ResetPasswordUseCase resetPassword,
                @Value("${nitida.admin.totp-required:true}") boolean totpRequired) {
        this.register = register;
        this.login = login;
        this.identity = identity;
        this.setupTotp = setupTotp;
        this.confirmTotp = confirmTotp;
        this.requestReset = requestReset;
        this.resetPassword = resetPassword;
        this.totpRequired = totpRequired;
    }

    ServerResponse register(ServerRequest req) throws Exception {
        var body = req.body(RegisterRequest.class);
        var result = register.execute(new RegisterUserUseCase.Command(body.email(), body.password(), body.displayName(), req.servletRequest().getRemoteAddr()));
        return ServerResponse.status(HttpStatus.CREATED).body(result);
    }

    ServerResponse login(ServerRequest req) throws Exception {
        var body = req.body(LoginRequest.class);
        HttpServletRequest http = req.servletRequest();
        var result = login.execute(new LoginUseCase.Command(body.email(), body.password(), body.totp(), http.getRemoteAddr()));
        startSession(http, result.user(), result.adminAllowed(), true);
        return ServerResponse.ok().body(me(result.user()));
    }

    ServerResponse me(ServerRequest req) {
        return ServerResponse.ok().body(me(AuthenticatedUser.current()));
    }

    ServerResponse setupTotp(ServerRequest req) {
        var result = setupTotp.execute(AuthenticatedUser.current().userId());
        return ServerResponse.ok().header("Cache-Control", "no-store").body(result);
    }

    /** Turning TOTP on is what lifts the "enroll first" restriction of a platform admin's current session. */
    ServerResponse confirmTotp(ServerRequest req) throws Exception {
        var user = AuthenticatedUser.current();
        var result = confirmTotp.execute(user.userId(), req.body(CodeRequest.class).code());
        if (result.platformAdmin()) startSession(req.servletRequest(), identity.findUser(user.userId()).orElseThrow(ApiException::forbidden), true, false);
        return ServerResponse.ok().header("Cache-Control", "no-store").body(Map.of("recoveryCodes", result.recoveryCodes()));
    }

    ServerResponse forgotPassword(ServerRequest req) throws Exception {
        var body = req.body(EmailRequest.class);
        requestReset.execute(body.email(), req.servletRequest().getRemoteAddr());
        return ServerResponse.noContent().build();
    }

    ServerResponse resetPassword(ServerRequest req) throws Exception {
        var body = req.body(ResetPasswordRequest.class);
        resetPassword.execute(body.token(), body.password());
        return ServerResponse.noContent().build();
    }

    private MeResponse me(IdentityApi.UserSummary u) {
        boolean setupRequired = u.platformAdmin() && !u.totpEnabled() && totpRequired;
        return new MeResponse(u.id(), u.email(), u.displayName(), u.platformAdmin(), u.totpEnabled(), setupRequired, identity.membershipsOf(u.id()));
    }

    private MeResponse me(AuthenticatedUser u) {
        var summary = identity.findUser(u.userId()).orElseThrow(ApiException::forbidden);
        return me(summary);
    }

    /**
     * New session id on login prevents session fixation (section 10). The admin authority is granted only when TOTP is
     * in place: until then a platform admin is an ordinary user who can enroll.
     */
    private static void startSession(HttpServletRequest http, IdentityApi.UserSummary user, boolean adminAllowed, boolean newSession) {
        var principal = new AuthenticatedUser(user.id(), user.email(), user.platformAdmin());
        var authorities = user.platformAdmin() && adminAllowed
                ? AuthorityUtils.createAuthorityList("ROLE_USER", "ROLE_PLATFORM_ADMIN")
                : AuthorityUtils.createAuthorityList("ROLE_USER");
        SecurityContext ctx = new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities));
        if (newSession && http.getSession(false) != null) http.changeSessionId();
        http.getSession(true).setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, ctx);
    }
}
