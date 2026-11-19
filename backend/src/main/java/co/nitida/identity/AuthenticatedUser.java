package co.nitida.identity;

import co.nitida.shared.ApiException;
import java.io.Serializable;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;

/** Principal stored in the server-side session. */
public record AuthenticatedUser(UUID userId, String email, boolean platformAdmin) implements Serializable {

    /** Spring Session indexes by principal name: keep it to the id, never the email (no PII in tables or logs). */
    @Override
    public String toString() {
        return userId.toString();
    }

    /** The logged-in user of this request; 403 if there is none. */
    public static AuthenticatedUser current() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUser u)) throw ApiException.forbidden();
        return u;
    }
}
