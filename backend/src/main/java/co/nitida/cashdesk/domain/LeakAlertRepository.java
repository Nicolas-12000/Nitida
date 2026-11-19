package co.nitida.cashdesk.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeakAlertRepository extends JpaRepository<LeakAlert, UUID> {
    boolean existsByWorkshopIdAndDedupeKey(UUID workshopId, String dedupeKey);

    Optional<LeakAlert> findByIdAndWorkshopId(UUID id, UUID workshopId);

    List<LeakAlert> findByWorkshopIdAndResolvedAtIsNullOrderByCreatedAtDesc(UUID workshopId, Pageable page);

    List<LeakAlert> findByWorkshopIdAndResolvedAtIsNotNullOrderByCreatedAtDesc(UUID workshopId, Pageable page);
}
