package co.nitida.identity.application;

import co.nitida.identity.IdentityApi;
import co.nitida.identity.domain.AppUser;
import co.nitida.identity.domain.AppUserRepository;
import co.nitida.identity.domain.Membership;
import co.nitida.identity.domain.MembershipRepository;
import co.nitida.shared.Role;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class IdentityApiImpl implements IdentityApi {
    private final AppUserRepository users;
    private final MembershipRepository memberships;

    IdentityApiImpl(AppUserRepository users, MembershipRepository memberships) {
        this.users = users;
        this.memberships = memberships;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserSummary> findUser(UUID userId) {
        return users.findById(userId).map(IdentityApiImpl::summary);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Role> activeRoles(UUID userId, UUID workshopId) {
        return memberships.findByUserIdAndWorkshopIdAndActiveTrue(userId, workshopId).stream()
                .map(Membership::getRole).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkshopRole> membershipsOf(UUID userId) {
        return memberships.findByUserIdAndActiveTrue(userId).stream()
                .map(m -> new WorkshopRole(m.getWorkshopId(), m.getRole())).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemberView> membersOf(UUID workshopId) {
        var rows = memberships.findByWorkshopId(workshopId);
        // one query for all users instead of one per membership (no N+1)
        Map<UUID, AppUser> byId = users.findAllById(rows.stream().map(Membership::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(AppUser::getId, u -> u));
        return rows.stream().map(m -> {
            var u = byId.get(m.getUserId());
            return new MemberView(m.getUserId(), u.getEmail(), u.getDisplayName(), m.getRole(), m.isActive());
        }).toList();
    }

    @Override
    @Transactional
    public void grant(UUID userId, UUID workshopId, Role role) {
        var existing = memberships.findByUserIdAndWorkshopId(userId, workshopId).stream()
                .filter(m -> m.getRole() == role).findFirst();
        existing.ifPresentOrElse(Membership::activate,
                () -> memberships.save(new Membership(userId, workshopId, role)));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isPlatformAdmin(UUID userId) {
        return users.findById(userId).map(AppUser::isPlatformAdmin).orElse(false);
    }

    static UserSummary summary(AppUser u) {
        return new UserSummary(u.getId(), u.getEmail(), u.getDisplayName(), u.isPlatformAdmin(), u.isTotpEnabled());
    }
}
