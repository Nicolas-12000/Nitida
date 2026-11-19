package co.nitida.orders.domain;

import co.nitida.orders.OrderStatus;
import co.nitida.shared.ApiException;
import co.nitida.shared.CopAmount;
import co.nitida.shared.Texts;
import co.nitida.shared.events.OrderDelivered;
import co.nitida.shared.persistence.AssignedIdEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

/** Aggregate root of a visit to the workshop. Every business rule of the order lives here (RN-40..49). */
@Entity
@Table(schema = "orders", name = "work_order")
public class WorkOrder extends AssignedIdEntity {
    @Column(name = "workshop_id", nullable = false)
    private UUID workshopId;
    @Column(name = "intake_id", nullable = false, unique = true)
    private UUID intakeId;
    @Column(name = "motorcycle_id", nullable = false)
    private UUID motorcycleId;
    @Column(name = "customer_id")
    private UUID customerId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.OPEN;
    @Column(nullable = false)
    private String diagnosis;
    @Column(name = "intake_mileage", nullable = false)
    private int intakeMileage;
    @Column(name = "labor_cost", nullable = false)
    private long laborCost;
    @Column(name = "warranty_days")
    private Integer warrantyDays;
    @Column(name = "warranty_km")
    private Integer warrantyKm;
    @Column(name = "is_warranty", nullable = false)
    private boolean warranty;
    @Column(name = "warranty_ref_order_id")
    private UUID warrantyRefOrderId;
    @Column(name = "mechanic_id")
    private UUID mechanicId;
    @Column(name = "cancel_reason")
    private String cancelReason;
    @Column(name = "reopen_reason")
    private String reopenReason;
    @Column(name = "claim_code_hash")
    private String claimCodeHash;
    @Column(name = "delivered_at")
    private Instant deliveredAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "work_order_id", nullable = false)
    @Fetch(FetchMode.SUBSELECT)
    private List<Job> jobs = new ArrayList<>();
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "work_order_id", nullable = false)
    @Fetch(FetchMode.SUBSELECT)
    private List<Part> parts = new ArrayList<>();
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "work_order_id", nullable = false)
    @Fetch(FetchMode.SUBSELECT)
    private List<OrderPhoto> photos = new ArrayList<>();

    protected WorkOrder() {}

    /** RN-41: an order starts from an intake, with a mandatory diagnosis (the "why"). */
    public static WorkOrder open(UUID id, UUID workshopId, UUID intakeId, UUID motorcycleId, UUID customerId,
                                 String diagnosis, int intakeMileage, UUID mechanicId, Instant now) {
        var o = new WorkOrder();
        o.id = id;
        o.workshopId = workshopId;
        o.intakeId = intakeId;
        o.motorcycleId = motorcycleId;
        o.customerId = customerId;
        o.diagnosis = validDiagnosis(diagnosis);
        o.intakeMileage = intakeMileage;
        o.mechanicId = mechanicId;
        o.createdAt = now;
        o.updatedAt = now;
        return o;
    }

    private static String validDiagnosis(String d) {
        return Texts.requireLength(d, 10, 2000, "INVALID_DIAGNOSIS", "El diagnóstico debe tener entre 10 y 2000 caracteres");
    }

    /** RN-46: editable until delivered (a cancelled order is closed too). */
    public boolean isEditable() {
        return status == OrderStatus.OPEN || status == OrderStatus.IN_PROGRESS || status == OrderStatus.COMPLETED;
    }

    private void requireEditable() {
        if (!isEditable()) {
            throw ApiException.conflict("ORDER_NOT_EDITABLE", "La orden ya no se puede modificar; use una enmienda del historial");
        }
    }

    public void checkVersion(long expected) {
        if (version != expected) {
            throw ApiException.conflict("VERSION_CONFLICT", "Otra persona modificó esta orden, recargue e intente de nuevo");
        }
    }

    /** Null arguments leave the field unchanged. */
    public void edit(Instant now, String diagnosis, Long laborCost, Integer warrantyDays, Integer warrantyKm, List<Job> newJobs) {
        requireEditable();
        if (diagnosis != null) this.diagnosis = validDiagnosis(diagnosis);
        if (laborCost != null) this.laborCost = CopAmount.line(laborCost).value();
        if (warrantyDays != null) {
            if (warrantyDays < 0 || warrantyDays > 3650) throw ApiException.unprocessable("INVALID_WARRANTY", "Los días de garantía deben estar entre 0 y 3650");
            this.warrantyDays = warrantyDays;
        }
        if (warrantyKm != null) {
            if (warrantyKm < 0 || warrantyKm > 100_000) throw ApiException.unprocessable("INVALID_WARRANTY", "Los km de garantía deben estar entre 0 y 100.000");
            this.warrantyKm = warrantyKm;
        }
        if (newJobs != null) {
            jobs.clear();
            jobs.addAll(newJobs);
        }
        touch(now);
    }

    public void addPart(Part part, Instant now) {
        requireEditable();
        parts.add(part);
        touch(now);
    }

    public Part removePart(UUID partId, Instant now) {
        requireEditable();
        Part part = findPart(partId);
        parts.remove(part);
        touch(now);
        return part;
    }

    public void addPhoto(OrderPhoto photo, Instant now) {
        requireEditable();
        photos.add(photo);
        touch(now);
    }

    /** Touches the order when a part changes (serial, authenticity) so it counts as activity (F5). */
    public void markChanged(Instant now) {
        requireEditable();
        touch(now);
    }

    public Part findPart(UUID partId) {
        return parts.stream().filter(p -> p.getId().equals(partId)).findFirst().orElseThrow(ApiException::notFound);
    }

    /** RN-47: a total of 0 is only allowed for a warranty order that points to an earlier order. */
    public void markAsWarranty(UUID referencedOrderId) {
        this.warranty = true;
        this.warrantyRefOrderId = referencedOrderId;
    }

    /** RN-44: sum of lines plus labor, with exact arithmetic (never overflows silently). */
    public CopAmount total() {
        CopAmount sum = new CopAmount(laborCost);
        for (Part p : parts) sum = sum.plus(p.lineTotal());
        return sum;
    }

    /**
     * RN-40 transitions except delivery. A cancellation needs a reason; reopening a completed order needs a reason;
     * completing needs at least one job (RN-41).
     */
    public void transition(OrderStatus target, String reason, Instant now) {
        if (target == null || target == OrderStatus.DELIVERED || !status.canMoveTo(target)) {
            throw ApiException.conflict("INVALID_TRANSITION", "La orden no puede pasar de " + status + " a " + target);
        }
        switch (target) {
            case COMPLETED -> {
                if (jobs.isEmpty()) throw ApiException.unprocessable("JOBS_REQUIRED", "Registre al menos un trabajo antes de completar la orden");
            }
            case CANCELLED -> cancelReason = requireReason(reason);
            case IN_PROGRESS -> {
                if (status == OrderStatus.COMPLETED) reopenReason = requireReason(reason);
            }
            default -> { }
        }
        status = target;
        touch(now);
    }

    private static String requireReason(String reason) {
        return Texts.requireLength(reason, 5, 500, "REASON_REQUIRED", "Indique el motivo (5 a 500 caracteres)");
    }

    /** RN-49: cancelling an order that already has parts raises F2, so the caller needs to know. */
    public boolean hasParts() {
        return !parts.isEmpty();
    }

    /** Payments that can still be added without exceeding the total (RN-51 and RN-47). */
    public void checkCanReceivePayment(PaymentMethod method, long amount, long alreadyPaid) {
        if (status.terminal()) throw ApiException.conflict("ORDER_NOT_EDITABLE", "La orden ya está cerrada");
        if (method == PaymentMethod.STORE_CREDIT && customerId == null) {
            throw ApiException.unprocessable("CUSTOMER_REQUIRED", "El fiado requiere un cliente identificado en la orden");
        }
        if (alreadyPaid + amount > total().value()) {
            throw ApiException.unprocessable("OVERPAYMENT", "Los pagos no pueden superar el total de la orden");
        }
    }

    /** RN-47 + UC-11: COMPLETED -> DELIVERED when payments equal the total (STORE_CREDIT counts as a payment). */
    public void deliver(List<Payment> payments, Instant now) {
        if (status != OrderStatus.COMPLETED) {
            throw ApiException.conflict("INVALID_TRANSITION", "Solo se puede entregar una orden completada");
        }
        long total = total().value();
        long paid = payments.stream().filter(p -> !p.isVoided()).mapToLong(Payment::getAmount).sum();
        if (total == 0 && !(warranty && warrantyRefOrderId != null)) {
            throw ApiException.unprocessable("INVALID_WARRANTY", "Una orden en $0 solo se permite como garantía de una orden anterior");
        }
        if (paid != total) {
            throw ApiException.unprocessable("INCOMPLETE_PAYMENTS", "La suma de los pagos debe ser igual al total de la orden");
        }
        status = OrderStatus.DELIVERED;
        deliveredAt = now;
        touch(now);
    }

    public void assignClaimCode(String hash) {
        this.claimCodeHash = hash;
    }

    /** RN-48: immutable snapshot published with OrderDelivered. */
    public OrderDelivered snapshot(List<String> photoHashes) {
        return new OrderDelivered(id, workshopId, motorcycleId, intakeMileage, diagnosis,
                jobs.stream().map(j -> new OrderDelivered.Job(j.getType().name(), j.getDescription())).toList(),
                parts.stream().map(p -> new OrderDelivered.Part(p.getName(), p.getReference(), p.getBrand(), p.getCondition().name(),
                        p.getQuantity(), p.getUnitPrice(), p.getProductCode(), p.getSerialNormalized(), p.getEvidenceLevel().name(),
                        p.getAuthenticityMethod(), p.getAuthenticityPhotoId() != null, p.getReplacementPhotoId() != null)).toList(),
                laborCost, total().value(), photoHashes, mechanicId, warrantyDays, warrantyKm, deliveredAt);
    }

    private void touch(Instant now) {
        updatedAt = now;
    }

    public UUID getWorkshopId() { return workshopId; }
    public UUID getIntakeId() { return intakeId; }
    public UUID getMotorcycleId() { return motorcycleId; }
    public UUID getCustomerId() { return customerId; }
    public OrderStatus getStatus() { return status; }
    public String getDiagnosis() { return diagnosis; }
    public int getIntakeMileage() { return intakeMileage; }
    public long getLaborCost() { return laborCost; }
    public Integer getWarrantyDays() { return warrantyDays; }
    public Integer getWarrantyKm() { return warrantyKm; }
    public boolean isWarranty() { return warranty; }
    public UUID getMechanicId() { return mechanicId; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
    public List<Job> getJobs() { return List.copyOf(jobs); }
    public List<Part> getParts() { return List.copyOf(parts); }
    public List<OrderPhoto> getPhotos() { return List.copyOf(photos); }
}
