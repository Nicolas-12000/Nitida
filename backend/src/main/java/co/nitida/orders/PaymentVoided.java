package co.nitida.orders;

import java.time.Instant;
import java.util.UUID;

public record PaymentVoided(UUID paymentId, UUID orderId, UUID workshopId, String method, long amount, Instant occurredAt) {}
