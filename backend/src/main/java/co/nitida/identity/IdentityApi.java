package co.nitida.identity;

import co.nitida.shared.Role;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Public API of the identity module: what other modules may ask about users and memberships. */
public interface IdentityApi {
    record UserSummary(UUID id, String email, String displayName, boolean platformAdmin, boolean totpEnabled) {}

    record WorkshopRole(UUID workshopId, Role role) {}

    record MemberView(UUID userId, String email, String displayName, Role role, boolean active) {}

    Optional<UserSummary> findUser(UUID userId);

    /** Active roles of the user in that workshop; empty when the user is not a member. */
    Set<Role> activeRoles(UUID userId, UUID workshopId);

    List<WorkshopRole> membershipsOf(UUID userId);

    List<MemberView> membersOf(UUID workshopId);

    void grant(UUID userId, UUID workshopId, Role role);

    boolean isPlatformAdmin(UUID userId);
}
