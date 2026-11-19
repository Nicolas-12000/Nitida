package co.nitida.intake;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Public API of the intake module. */
public interface IntakeApi {
    record IntakeInfo(UUID id, UUID workshopId, UUID motorcycleId, int mileage, Instant createdAt) {}

    /** The intake must be OPEN and belong to the workshop; otherwise empty. */
    Optional<IntakeInfo> findOpen(UUID intakeId, UUID workshopId);

    List<IntakeInfo> open(UUID workshopId);

    /** Open intakes of every workshop created before the cut-off (leak alert F1). */
    List<IntakeInfo> openCreatedBefore(Instant cutoff);

    /** Closes the intake by attaching it to a work order, inside the caller's transaction (RN-31). */
    void linkToOrder(UUID intakeId, UUID workshopId, UUID workOrderId);
}
