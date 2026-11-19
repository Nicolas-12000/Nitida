package co.nitida.shared.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Immutable snapshot of a delivered work order (RN-48). It lives in the shared kernel, not in orders, so that
 * history can listen to it without depending on orders (which already depends on history): no module cycle.
 */
public record OrderDelivered(UUID orderId, UUID workshopId, UUID motorcycleId, int mileage, String diagnosis,
                             List<Job> jobs, List<Part> parts, long laborCost, long total, List<String> photoHashes,
                             UUID mechanicId, Integer warrantyDays, Integer warrantyKm, Instant deliveredAt) {
    public OrderDelivered {
        jobs = List.copyOf(jobs);
        parts = List.copyOf(parts);
        photoHashes = List.copyOf(photoHashes);
    }

    public record Job(String type, String description) {}

    /** Prices and the serial in clear belong to the private content; the public content gets hashes only (RN-97). */
    public record Part(String name, String reference, String brand, String condition, int quantity, long unitPrice,
                       String productCode, String serial, String evidenceLevel, String authenticityMethod,
                       boolean hasAuthenticityPhoto, boolean hasReplacementPhoto) {}
}
