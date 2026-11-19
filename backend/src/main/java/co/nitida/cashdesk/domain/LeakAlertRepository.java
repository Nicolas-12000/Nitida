package co.nitida.cashdesk.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeakAlertRepository extends JpaRepository<LeakAlert, UUID> {
    @Query("select a.dedupeKey from LeakAlert a where a.dedupeKey in :keys")
    Set<String> findExistingKeys(@Param("keys") Collection<String> keys);

    boolean existsByWorkshopIdAndDedupeKey(UUID workshopId, String dedupeKey);

    Optional<LeakAlert> findByIdAndWorkshopId(UUID id, UUID workshopId);

    List<LeakAlert> findByWorkshopIdAndResolvedAtIsNullOrderByCreatedAtDesc(UUID workshopId, Pageable page);

    List<LeakAlert> findByWorkshopIdAndResolvedAtIsNotNullOrderByCreatedAtDesc(UUID workshopId, Pageable page);
}
