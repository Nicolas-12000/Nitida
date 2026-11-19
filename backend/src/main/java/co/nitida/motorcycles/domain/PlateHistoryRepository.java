package co.nitida.motorcycles.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlateHistoryRepository extends JpaRepository<PlateHistory, UUID> {
    Optional<PlateHistory> findByMotorcycleIdAndValidToIsNull(UUID motorcycleId);
}
