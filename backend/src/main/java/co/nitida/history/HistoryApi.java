package co.nitida.history;

import co.nitida.shared.Mileage;
import java.util.Optional;
import java.util.UUID;

/** Public API of the history module. */
public interface HistoryApi {
    /** Reference mileage for RN-45: last known reading; an ODOMETER_CHANGE resets it (RN-63). */
    Optional<Mileage> lastMileage(UUID motorcycleId);

    /** Recomputes hashes and signatures in order (section 8.3). Never throws: fail closed. */
    VerificationResult verify(UUID motorcycleId);
}
