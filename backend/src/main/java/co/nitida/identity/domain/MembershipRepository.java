package co.nitida.identity.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipRepository extends JpaRepository<Membership, MembershipId> {
    List<Membership> findByUserIdAndActiveTrue(UUID userId);

    List<Membership> findByUserIdAndWorkshopIdAndActiveTrue(UUID userId, UUID workshopId);

    List<Membership> findByWorkshopId(UUID workshopId);

    List<Membership> findByUserIdAndWorkshopId(UUID userId, UUID workshopId);

    long countByWorkshopIdAndRoleAndActiveTrue(UUID workshopId, co.nitida.shared.Role role);
}
