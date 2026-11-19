package co.nitida.motorcycles;

import java.time.Instant;
import java.util.UUID;

/** RN-82: the motorcycle changed hands. Carries no user ids or names: the history entry must not have them (RN-22). */
public record OwnershipTransferred(UUID motorcycleId, UUID transferId, Instant occurredAt) {}
