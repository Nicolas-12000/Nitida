package co.nitida.intake;

import java.time.Instant;
import java.util.UUID;

public record IntakeRegistered(UUID intakeId, UUID workshopId, UUID motorcycleId, int mileage, Instant occurredAt) {}
