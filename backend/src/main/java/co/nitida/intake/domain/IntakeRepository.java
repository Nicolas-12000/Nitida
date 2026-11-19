package co.nitida.intake.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntakeRepository extends JpaRepository<Intake, UUID> {
    Optional<Intake> findByIdAndWorkshopId(UUID id, UUID workshopId);

    List<Intake> findByWorkshopIdAndStatusOrderByCreatedAtAsc(UUID workshopId, IntakeStatus status);

    boolean existsByWorkshopIdAndMotorcycleId(UUID workshopId, UUID motorcycleId);

    boolean existsByWorkshopIdAndMotorcycleIdAndStatus(UUID workshopId, UUID motorcycleId, IntakeStatus status);

    List<Intake> findByStatusAndCreatedAtBefore(IntakeStatus status, Instant cutoff);
}
