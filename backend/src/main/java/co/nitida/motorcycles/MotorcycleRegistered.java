package co.nitida.motorcycles;

import java.time.Instant;
import java.util.UUID;

/** First appearance of a motorcycle on the platform (history starts with INITIAL_REGISTRATION). */
public record MotorcycleRegistered(UUID motorcycleId, int declaredMileage, UUID workshopId, Instant occurredAt) {}
