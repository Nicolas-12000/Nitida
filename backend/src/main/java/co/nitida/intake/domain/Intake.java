package co.nitida.intake.domain;

import co.nitida.shared.ApiException;
import co.nitida.shared.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "intake", name = "intake")
public class Intake extends AssignedIdEntity {
    @Column(name = "workshop_id", nullable = false)
    private UUID workshopId;
    @Column(name = "motorcycle_id", nullable = false)
    private UUID motorcycleId;
    @Column(nullable = false)
    private int mileage;
    @Column(name = "odometer_photo_id", nullable = false)
    private UUID odometerPhotoId;
    @Column(name = "motorcycle_photo_id", nullable = false)
    private UUID motorcyclePhotoId;
    @Column(name = "received_by", nullable = false)
    private UUID receivedBy;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IntakeStatus status = IntakeStatus.OPEN;
    @Enumerated(EnumType.STRING)
    @Column(name = "discard_reason")
    private DiscardReason discardReason;
    @Column(name = "work_order_id")
    private UUID workOrderId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Version
    private long version;

    protected Intake() {}

    /** RN-30: motorcycle, dashboard km, both photos, who received it and when. */
    public static Intake register(UUID id, UUID workshopId, UUID motorcycleId, int mileage, UUID odometerPhotoId,
                                  UUID motorcyclePhotoId, UUID receivedBy, Instant now) {
        if (odometerPhotoId == null || motorcyclePhotoId == null) {
            throw ApiException.unprocessable("PHOTO_REQUIRED", "Se requieren la foto del tablero y la foto de la moto");
        }
        var i = new Intake();
        i.id = id;
        i.workshopId = workshopId;
        i.motorcycleId = motorcycleId;
        i.mileage = mileage;
        i.odometerPhotoId = odometerPhotoId;
        i.motorcyclePhotoId = motorcyclePhotoId;
        i.receivedBy = receivedBy;
        i.createdAt = now;
        return i;
    }

    /** RN-31: an OPEN intake ends attached to an order or discarded with a reason. */
    public void discard(DiscardReason reason) {
        requireOpen();
        if (reason == null) throw ApiException.unprocessable("REASON_REQUIRED", "Indique el motivo del descarte");
        status = IntakeStatus.DISCARDED;
        discardReason = reason;
    }

    public void linkToOrder(UUID orderId) {
        requireOpen();
        status = IntakeStatus.LINKED;
        workOrderId = orderId;
    }

    private void requireOpen() {
        if (status != IntakeStatus.OPEN) {
            throw ApiException.conflict("INTAKE_NOT_OPEN", "La recepción ya fue cerrada");
        }
    }

    public UUID getWorkshopId() { return workshopId; }
    public UUID getMotorcycleId() { return motorcycleId; }
    public int getMileage() { return mileage; }
    public UUID getReceivedBy() { return receivedBy; }
    public IntakeStatus getStatus() { return status; }
    public DiscardReason getDiscardReason() { return discardReason; }
    public UUID getWorkOrderId() { return workOrderId; }
    public Instant getCreatedAt() { return createdAt; }
}
