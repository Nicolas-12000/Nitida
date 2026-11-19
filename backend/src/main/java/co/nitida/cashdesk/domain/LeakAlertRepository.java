package co.nitida.cashdesk.domain;

import java.time.Instant;
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

    @Query("""
            select a from LeakAlert a
            where a.workshopId = :workshopId
              and ((:resolved = true and a.resolvedAt is not null) or (:resolved = false and a.resolvedAt is null))
            order by a.createdAt desc, a.id desc""")
    List<LeakAlert> firstPage(@Param("workshopId") UUID workshopId, @Param("resolved") boolean resolved, Pageable page);

    @Query("""
            select a from LeakAlert a
            where a.workshopId = :workshopId
              and ((:resolved = true and a.resolvedAt is not null) or (:resolved = false and a.resolvedAt is null))
              and (a.createdAt < :at or (a.createdAt = :at and a.id < :id))
            order by a.createdAt desc, a.id desc""")
    List<LeakAlert> pageAfter(@Param("workshopId") UUID workshopId, @Param("resolved") boolean resolved,
                              @Param("at") Instant at, @Param("id") UUID id, Pageable page);
}
