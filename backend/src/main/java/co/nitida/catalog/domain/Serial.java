package co.nitida.catalog.domain;

import co.nitida.shared.ApiException;
import co.nitida.shared.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "catalog", name = "serial")
public class Serial extends AssignedIdEntity {
    @Column(name = "normalized_serial", nullable = false)
    private String normalizedSerial;
    @Column(name = "serial_hash", nullable = false)
    private String serialHash;
    @Column(name = "motorcycle_id", nullable = false)
    private UUID motorcycleId;
    @Column(name = "workshop_id", nullable = false)
    private UUID workshopId;
    @Column(name = "work_order_id")
    private UUID workOrderId;
    @Column(nullable = false)
    private String status;
    /** Equals the serial while ACTIVE and null afterwards: backs the unique constraint. */
    @Column(name = "active_serial")
    private String activeSerial;
    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;
    @Column(name = "retired_at")
    private Instant retiredAt;
    private String reason;

    protected Serial() {}

    public static Serial activate(UUID id, SerialNumber serial, UUID motorcycleId, UUID workshopId, UUID orderId, Instant now) {
        var s = new Serial();
        s.id = id;
        s.normalizedSerial = serial.value();
        s.serialHash = serial.hash();
        s.motorcycleId = motorcycleId;
        s.workshopId = workshopId;
        s.workOrderId = orderId;
        s.status = "ACTIVE";
        s.activeSerial = serial.value();
        s.registeredAt = now;
        return s;
    }

    /** Reason text rule shared by retiring and reactivating (5..200 characters). */
    public static String requireReason(String reason, String code, String message) {
        String r = reason == null ? "" : reason.strip();
        if (r.length() < 5 || r.length() > 200) throw ApiException.unprocessable(code, message);
        return r;
    }

    /** RN-94: a retired serial leaves the unique index so another motorcycle may take it later. */
    public void retire(String reason, Instant now) {
        String r = requireReason(reason, "REASON_REQUIRED", "Indique el motivo (5 a 200 caracteres)");
        if (!isActive()) throw ApiException.conflict("SERIAL_NOT_ACTIVE", "El serial ya está retirado");
        status = "RETIRED";
        activeSerial = null;
        retiredAt = now;
        reason = r;
    }

    /** Only with a reason, e.g. "pieza reparada y reinstalada". */
    public void reactivate(UUID motorcycleId, UUID workshopId, UUID orderId, String reason, Instant now) {
        String r = requireReason(reason, "REACTIVATION_REASON_REQUIRED", "Ese serial fue retirado: indique el motivo para reactivarlo");
        this.motorcycleId = motorcycleId;
        this.workshopId = workshopId;
        this.workOrderId = orderId;
        this.status = "ACTIVE";
        this.activeSerial = normalizedSerial;
        this.registeredAt = now;
        this.retiredAt = null;
        this.reason = r;
    }

    public boolean isActive() { return "ACTIVE".equals(status); }

    public String getNormalizedSerial() { return normalizedSerial; }
    public String getSerialHash() { return serialHash; }
    public UUID getMotorcycleId() { return motorcycleId; }
    public UUID getWorkshopId() { return workshopId; }
    public String getStatus() { return status; }
}
