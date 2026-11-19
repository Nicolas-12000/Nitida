package co.nitida.catalog;

import java.util.Optional;
import java.util.UUID;

/** Public API of the catalog module. */
public interface CatalogApi {
    record ProductInfo(UUID id, String code, String codeType, String name, String brand, String reference, String status) {}

    sealed interface SerialRegistration {
        /** The serial is now (or already was) active on this motorcycle. */
        record Accepted(UUID serialId, String serialHash) implements SerialRegistration {}

        /** Active elsewhere. Deliberately carries no information about where (RN-94). */
        record Rejected(String serialHash) implements SerialRegistration {}
    }

    Optional<ProductInfo> findProduct(UUID workshopId, String code);

    /** Validates the code (RN-92) and stores a product proposed by the workshop (RN-93). */
    ProductInfo proposeProduct(UUID workshopId, String code, String name, String brand, String reference);

    /**
     * Same transaction as the caller, so two workshops cannot both activate one serial (RN-94).
     * {@code reactivationReason} is only needed to bring back a RETIRED serial.
     */
    SerialRegistration registerSerial(UUID workshopId, UUID motorcycleId, UUID orderId, String rawSerial, String reactivationReason);

    /** Retires every serial registered by an order (cancelled order releases them). */
    void releaseForOrder(UUID orderId);

    /** True when this serial hash is active on the given motorcycle (used to keep evidence honest). */
    boolean isActiveOn(UUID serialId, UUID motorcycleId);
}
