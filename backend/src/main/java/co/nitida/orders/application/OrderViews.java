package co.nitida.orders.application;

import co.nitida.orders.OrderStatus;
import co.nitida.orders.domain.Payment;
import co.nitida.orders.domain.WorkOrder;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read model of an order for the workshop screens. */
public final class OrderViews {
    private OrderViews() {}

    public record JobView(String type, String description) {}

    public record PartView(UUID id, String name, String reference, String brand, String condition, int quantity, long unitPrice,
                           String productCode, boolean hasSerial, String evidenceLevel, String authenticityMethod) {}

    public record PaymentView(UUID id, String method, long amount, Instant recordedAt, boolean voided) {}

    public record OrderView(UUID id, UUID intakeId, UUID motorcycleId, UUID customerId, OrderStatus status, String diagnosis,
                            int mileage, long laborCost, long total, Integer warrantyDays, Integer warrantyKm, boolean warranty,
                            UUID mechanicId, Instant deliveredAt, long version, List<JobView> jobs, List<PartView> parts,
                            List<PaymentView> payments, Long paid) {}

    public record OrderSummary(UUID id, UUID motorcycleId, OrderStatus status, String diagnosis, long total, Instant updatedAt) {}

    /** Payments and what has been paid are cash-desk data: mechanics do not get them (section 3.1). */
    static OrderView of(WorkOrder o, List<Payment> payments, boolean includeMoney) {
        var paymentViews = includeMoney
                ? payments.stream().map(p -> new PaymentView(p.getId(), p.getMethod().name(), p.getAmount(), p.getRecordedAt(), p.isVoided())).toList()
                : List.<PaymentView>of();
        Long paid = includeMoney ? payments.stream().filter(p -> !p.isVoided()).mapToLong(Payment::getAmount).sum() : null;
        return new OrderView(o.getId(), o.getIntakeId(), o.getMotorcycleId(), o.getCustomerId(), o.getStatus(), o.getDiagnosis(),
                o.getIntakeMileage(), o.getLaborCost(), o.total().value(), o.getWarrantyDays(), o.getWarrantyKm(), o.isWarranty(),
                o.getMechanicId(), o.getDeliveredAt(), o.getVersion(),
                o.getJobs().stream().map(j -> new JobView(j.getType().name(), j.getDescription())).toList(),
                o.getParts().stream().map(p -> new PartView(p.getId(), p.getName(), p.getReference(), p.getBrand(), p.getCondition().name(),
                        p.getQuantity(), p.getUnitPrice(), p.getProductCode(), p.getSerialId() != null, p.getEvidenceLevel().name(),
                        p.getAuthenticityMethod())).toList(),
                paymentViews, paid);
    }
}
