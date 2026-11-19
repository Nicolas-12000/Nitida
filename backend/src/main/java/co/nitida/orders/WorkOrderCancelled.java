package co.nitida.orders;

import java.util.UUID;

public record WorkOrderCancelled(UUID orderId, UUID workshopId, boolean hadParts, String reason) {}
