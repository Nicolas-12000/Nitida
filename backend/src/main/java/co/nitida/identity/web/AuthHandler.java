package co.nitida.identity.web;

import co.nitida.identity.AuthenticatedUser;
import co.nitida.identity.IdentityApi;
import co.nitida.identity.application.LoginUseCase;
import co.nitida.identity.application.RegisterUserUseCase;
import co.nitida.shared.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/** Thin: HTTP in, use case, HTTP out. */
@Component
class AuthHandler {
    record RegisterRequest(String email, String password, String displayName) {}

    record LoginRequest(String email, String password) {}

    record MeResponse(UUID id, String email, String displayName, boolean platformAdmin,
                      List<IdentityApi.WorkshopRole> memberships) {}

    private final RegisterUserUseCase register;
    private final LoginUseCase login;
    private final IdentityApi identity;

    AuthHandler(RegisterUserUseCase register, LoginUseCase login, IdentityApi identity) {
        this.register = register;
        this.login = login;
        this.identity = identity;
    }

    ServerResponse register(ServerRequest req) throws Exception {
        var body = req.body(RegisterRequest.class);
        var result = register.execute(new RegisterUserUseCase.Command(body.email(), body.password(), body.displayName()));
        return ServerResponse.status(HttpStatus.CREATED).body(result);
    }

    ServerResponse login(ServerRequest req) throws Exception {
        var body = req.body(LoginRequest.class);
        HttpServletRequest http = req.servletRequest();
        var user = login.execute(new LoginUseCase.Command(body.email(), body.password(), http.getRemoteAddr()));
        startSession(http, user);
        return ServerResponse.ok().body(me(user));
    }

    ServerResponse me(ServerRequest req) {
        return ServerResponse.ok().body(me(AuthenticatedUser.current()));
    }

    private MeResponse me(IdentityApi.UserSummary u) {
        return new MeResponse(u.id(), u.email(), u.displayName(), u.platformAdmin(), identity.membershipsOf(u.id()));
    }

    private MeResponse me(AuthenticatedUser u) {
        var summary = identity.findUser(u.userId()).orElseThrow(ApiException::forbidden);
        return me(summary);
    }

    /** New session id on login prevents session fixation (section 10). */
    private static void startSession(HttpServletRequest http, IdentityApi.UserSummary user) {
        var principal = new AuthenticatedUser(user.id(), user.email(), user.platformAdmin());
        var authorities = user.platformAdmin()
                ? AuthorityUtils.createAuthorityList("ROLE_USER", "ROLE_PLATFORM_ADMIN")
                : AuthorityUtils.createAuthorityList("ROLE_USER");
        SecurityContext ctx = new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities));
        if (http.getSession(false) != null) http.changeSessionId();
        http.getSession(true).setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, ctx);
    }
}
