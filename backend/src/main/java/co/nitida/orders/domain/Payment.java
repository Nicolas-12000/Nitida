package co.nitida.orders.domain;

import co.nitida.shared.ApiException;
import co.nitida.shared.CopAmount;
import co.nitida.shared.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** RN-51: payments are never edited; they are voided with a reason and a new one is registered. */
@Entity
@Table(schema = "orders", name = "payment")
public class Payment extends AssignedIdEntity {
    @Column(name = "workshop_id", nullable = false)
    private UUID workshopId;
    @Column(name = "work_order_id", nullable = false)
    private UUID workOrderId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;
    @Column(nullable = false)
    private long amount;
    @Column(name = "recorded_by", nullable = false)
    private UUID recordedBy;
    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;
    @Column(name = "voided_at")
    private Instant voidedAt;
    @Column(name = "void_reason")
    private String voidReason;

    protected Payment() {}

    public Payment(UUID id, UUID workshopId, UUID workOrderId, PaymentMethod method, long amount, UUID recordedBy, Instant now) {
        super(id);
        if (method == null) throw ApiException.unprocessable("INVALID_METHOD", "Indique el medio de pago");
        if (amount <= 0) throw ApiException.unprocessable("INVALID_AMOUNT", "El valor del pago debe ser mayor a cero");
        this.amount = CopAmount.line(amount).value();
        this.workshopId = workshopId;
        this.workOrderId = workOrderId;
        this.method = method;
        this.recordedBy = recordedBy;
        this.recordedAt = now;
    }

    public void voidIt(String reason, Instant now) {
        String r = reason == null ? "" : reason.strip();
        if (r.length() < 5 || r.length() > 500) {
            throw ApiException.unprocessable("INVALID_REASON", "El motivo debe tener entre 5 y 500 caracteres");
        }
        if (voidedAt != null) throw ApiException.conflict("PAYMENT_ALREADY_VOIDED", "El pago ya fue anulado");
        voidedAt = now;
        voidReason = r;
    }

    public boolean isVoided() { return voidedAt != null; }

    public UUID getWorkshopId() { return workshopId; }
    public UUID getWorkOrderId() { return workOrderId; }
    public PaymentMethod getMethod() { return method; }
    public long getAmount() { return amount; }
    public Instant getRecordedAt() { return recordedAt; }
    public Instant getVoidedAt() { return voidedAt; }
}
