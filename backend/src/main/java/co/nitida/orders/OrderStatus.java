package co.nitida.orders;

import java.util.EnumSet;
import java.util.Set;

/** RN-40. DELIVERED is reached only through delivery (it needs payments), never through a plain transition. */
public enum OrderStatus {
    OPEN, IN_PROGRESS, COMPLETED, DELIVERED, CANCELLED;

    public Set<OrderStatus> allowedNext() {
        return switch (this) {
            case OPEN -> EnumSet.of(IN_PROGRESS, CANCELLED);
            case IN_PROGRESS -> EnumSet.of(COMPLETED, CANCELLED);
            case COMPLETED -> EnumSet.of(IN_PROGRESS, DELIVERED);
            case DELIVERED, CANCELLED -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    public boolean canMoveTo(OrderStatus target) {
        return allowedNext().contains(target);
    }

    public boolean terminal() {
        return this == DELIVERED || this == CANCELLED;
    }
}
