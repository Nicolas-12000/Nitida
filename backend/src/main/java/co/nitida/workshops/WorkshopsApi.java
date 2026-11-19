package co.nitida.workshops;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Public API of the workshops module. */
public interface WorkshopsApi {
    record WorkshopInfo(UUID id, String name, String municipality, WorkshopStatus status, Instant verifiedAt,
                        Instant suspendedAt, int intakeAlertHours, long cashTolerance, int stalledDays) {
        public boolean verified() { return status == WorkshopStatus.VERIFIED; }
    }

    record SignedHash(UUID keyId, byte[] signature) {}

    record PublicKeyInfo(UUID keyId, UUID workshopId, String algorithm, byte[] publicKey, boolean retired) {}

    Optional<WorkshopInfo> find(UUID workshopId);

    /** 404 if it does not exist, 423 WORKSHOP_SUSPENDED if suspended (RN-04). */
    WorkshopInfo requireActive(UUID workshopId);

    List<WorkshopInfo> all();

    /** Batch lookup by ids (no N+1 on list screens and public pages). */
    java.util.Map<UUID, WorkshopInfo> findAll(java.util.Collection<UUID> ids);

    /** Signs with the workshop's current Ed25519 key. Throws if it cannot (fail closed). */
    SignedHash sign(UUID workshopId, byte[] data);

    /** Public key (X.509 DER) by key id, retired keys included so old entries stay verifiable. */
    Optional<byte[]> publicKey(UUID keyId);

    List<PublicKeyInfo> publicKeys();
}
