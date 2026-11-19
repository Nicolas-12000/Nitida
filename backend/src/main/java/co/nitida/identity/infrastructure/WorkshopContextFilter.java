package co.nitida.identity.infrastructure;

import co.nitida.identity.AuthenticatedUser;
import co.nitida.identity.IdentityApi;
import co.nitida.shared.ApiException;
import co.nitida.shared.WorkshopContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Validates X-Workshop-Id against the user's memberships on every request (RN-05, section 9).
 * A workshop the user does not belong to answers 404, never revealing that it exists.
 */
@Component
class WorkshopContextFilter extends OncePerRequestFilter {
    static final String HEADER = "X-Workshop-Id";

    private final IdentityApi identity;
    private final HandlerExceptionResolver resolver;

    WorkshopContextFilter(IdentityApi identity, @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.identity = identity;
        this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        try {
            if (header != null && auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
                UUID workshopId = parse(header);
                var roles = identity.activeRoles(user.userId(), workshopId);
                if (roles.isEmpty()) throw ApiException.notFound();
                WorkshopContext.set(new WorkshopContext(workshopId, user.userId(), roles));
            }
            chain.doFilter(request, response);
        } catch (ApiException e) {
            resolver.resolveException(request, response, null, e);
        } finally {
            WorkshopContext.clear();
        }
    }

    private static UUID parse(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_WORKSHOP_ID", "El identificador del taller no es válido");
        }
    }
}
