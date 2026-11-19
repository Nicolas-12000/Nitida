package co.nitida.motorcycles;

import java.util.Optional;
import java.util.UUID;

/** Public API of the motorcycles module. */
public interface MotorcyclesApi {
    record MotorcycleInfo(UUID id, String vin, String plate, String maskedPlate, boolean legacyPlateFormat, String brand,
                          String line, Integer modelYear, Integer displacementCc) {}

    /** Declared data, not validated against external sources in the MVP (RN-16). */
    record MotorcycleDetails(String brand, String line, Integer modelYear, Integer displacementCc) {}

    record Registration(MotorcycleInfo motorcycle, boolean created) {}

    /**
     * RN-13..15: finds the motorcycle by VIN, then by plate; associates a late VIN with a plate-only motorcycle;
     * creates it (publishing MotorcycleRegistered) only when it does not exist yet.
     */
    Registration findOrRegister(UUID workshopId, String plate, String vin, MotorcycleDetails details, int declaredMileage);

    Optional<MotorcycleInfo> find(UUID motorcycleId);

    /** Batch lookup so list screens do not query once per row. */
    java.util.Map<UUID, MotorcycleInfo> findAll(java.util.Collection<UUID> motorcycleIds);

    Optional<MotorcycleInfo> lookup(String plate, String vin);

    /** RN-80: stores the hash of a single-use claim code handed out on delivery (the code is never stored in clear). */
    void issueClaimCode(UUID motorcycleId, String codeHash, java.time.Instant expiresAt);

    boolean customerBelongsTo(UUID customerId, UUID workshopId);

    /** When the user owned the motorcycle; {@code to} is null while they still own it. */
    record OwnershipPeriod(java.time.Instant from, java.time.Instant to) {
        public boolean active() { return to == null; }
    }

    /** The user's most recent ownership of the motorcycle, if any (RN-81, RN-82). */
    Optional<OwnershipPeriod> ownership(UUID userId, UUID motorcycleId);
}
