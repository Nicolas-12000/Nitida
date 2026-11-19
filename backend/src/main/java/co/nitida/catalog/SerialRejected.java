package co.nitida.catalog;

import java.time.Instant;
import java.util.UUID;

/** Only the workshop that tried and the serial hash: nothing about the other motorcycle or workshop (RN-94). */
public record SerialRejected(UUID workshopId, UUID orderId, String serialHash, Instant occurredAt) {}
