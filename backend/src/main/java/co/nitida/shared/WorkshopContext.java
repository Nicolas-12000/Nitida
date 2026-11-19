package co.nitida.shared;

import java.util.Set;
import java.util.UUID;

/** Active workshop and acting user for one request or one event (no HTTP session in listeners). */
public record WorkshopContext(UUID workshopId, UUID userId, Set<Role> roles) {
    private static final ThreadLocal<WorkshopContext> CURRENT = new ThreadLocal<>();

    public WorkshopContext {
        roles = Set.copyOf(roles);
    }

    public static WorkshopContext current() {
        var ctx = CURRENT.get();
        if (ctx == null) throw ApiException.badRequest("WORKSHOP_REQUIRED", "Falta indicar el taller activo");
        return ctx;
    }

    /** For code that must work with or without a request (transaction setup): empty when there is none. */
    public static java.util.Optional<WorkshopContext> currentOrEmpty() {
        return java.util.Optional.ofNullable(CURRENT.get());
    }

    /** The workshop named in the URL must be the active one validated from the header; otherwise 404. */
    public static WorkshopContext forPath(String pathWorkshopId) {
        var ctx = current();
        if (!ctx.workshopId().toString().equals(pathWorkshopId)) throw ApiException.notFound();
        return ctx;
    }

    public static void set(WorkshopContext ctx) { CURRENT.set(ctx); }

    public static void clear() { CURRENT.remove(); }

    /** Authorization lives in the use case, never in the frontend (RULE: server decides). */
    public WorkshopContext require(Role... allowed) {
        for (Role r : allowed) if (roles.contains(r)) return this;
        throw ApiException.forbidden();
    }

    public boolean has(Role role) { return roles.contains(role); }
}
