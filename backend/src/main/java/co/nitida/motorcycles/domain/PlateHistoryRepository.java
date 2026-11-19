package co.nitida.motorcycles.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlateHistoryRepository extends JpaRepository<PlateHistory, UUID> {}
