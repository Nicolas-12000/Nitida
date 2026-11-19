package co.nitida.history.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HistoryEntryRepository extends JpaRepository<HistoryEntry, UUID> {
    List<HistoryEntry> findByMotorcycleIdOrderBySeqAsc(UUID motorcycleId);

    Optional<HistoryEntry> findByMotorcycleIdAndSeq(UUID motorcycleId, long seq);

    boolean existsBySource(String source);

    boolean existsByWorkshopIdAndMotorcycleId(UUID workshopId, UUID motorcycleId);

    Optional<HistoryEntry> findFirstByMotorcycleIdAndTypeInOrderBySeqDesc(UUID motorcycleId, Collection<EntryType> types);
}
