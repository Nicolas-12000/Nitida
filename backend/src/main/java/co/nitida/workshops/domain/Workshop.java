package co.nitida.workshops.domain;

import co.nitida.shared.ApiException;
import co.nitida.shared.Texts;
import co.nitida.shared.persistence.AssignedIdEntity;
import co.nitida.workshops.WorkshopStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "workshops", name = "workshop")
public class Workshop extends AssignedIdEntity {
    @Column(nullable = false)
    private String name;
    @Column(name = "document_number", nullable = false)
    private String documentNumber;
    @Column(nullable = false)
    private String address;
    @Column(nullable = false)
    private String municipality;
    @Column(nullable = false)
    private String phone;
    @Column(name = "facade_photo_id")
    private UUID facadePhotoId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkshopStatus status = WorkshopStatus.PENDING_VERIFICATION;
    @Column(name = "verified_at")
    private Instant verifiedAt;
    @Column(name = "verified_by")
    private UUID verifiedBy;
    @Column(name = "suspended_at")
    private Instant suspendedAt;
    @Column(name = "intake_alert_hours", nullable = false)
    private int intakeAlertHours = 24;
    @Column(name = "cash_tolerance", nullable = false)
    private long cashTolerance;
    @Column(name = "stalled_days", nullable = false)
    private int stalledDays = 7;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Version
    private long version;

    protected Workshop() {}

    /** RN-01: born PENDING_VERIFICATION. */
    public static Workshop register(UUID id, String name, String documentNumber, String address, String municipality,
                                    String phone, UUID facadePhotoId, Instant now) {
        var w = new Workshop();
        w.id = id;
        w.name = Texts.requireLength(name, 2, 120, "INVALID_NAME", "El nombre comercial debe tener entre 2 y 120 caracteres");
        String doc = documentNumber == null ? "" : documentNumber.replaceAll("[\\s.]", "");
        if (!doc.matches("^[0-9]{5,15}(-[0-9])?$")) {
            throw ApiException.unprocessable("INVALID_DOCUMENT", "El NIT o la cédula no es válido");
        }
        w.documentNumber = doc;
        w.address = Texts.requireLength(address, 5, 200, "INVALID_ADDRESS", "La dirección debe tener entre 5 y 200 caracteres");
        w.municipality = Texts.requireLength(municipality, 2, 80, "INVALID_MUNICIPALITY", "El municipio no es válido");
        w.phone = co.nitida.shared.PhoneNumbers.normalize(phone);
        w.facadePhotoId = facadePhotoId;
        w.createdAt = now;
        return w;
    }

    /** RN-02: only moves along allowed edges; staying in the same state is a conflict. */
    public void changeStatus(WorkshopStatus target, UUID adminId, Instant now) {
        if (target == status || target == WorkshopStatus.PENDING_VERIFICATION) {
            throw ApiException.conflict("INVALID_TRANSITION", "El taller ya está en ese estado o la transición no es válida");
        }
        if (target == WorkshopStatus.VERIFIED) {
            verifiedAt = now;
            verifiedBy = adminId;
            suspendedAt = null;
        } else {
            suspendedAt = now;
        }
        status = target;
    }

    /** RN-32 (1..168 h), RN-50 F3 (0..50.000) and F5 (stalled days). */
    public void updateSettings(int intakeAlertHours, long cashTolerance, int stalledDays) {
        if (intakeAlertHours < 1 || intakeAlertHours > 168) {
            throw ApiException.unprocessable("INVALID_SETTING", "El umbral de recepción debe estar entre 1 y 168 horas");
        }
        if (cashTolerance < 0 || cashTolerance > 50_000) {
            throw ApiException.unprocessable("INVALID_SETTING", "La tolerancia de caja debe estar entre 0 y 50.000");
        }
        if (stalledDays < 1 || stalledDays > 90) {
            throw ApiException.unprocessable("INVALID_SETTING", "Los días de orden detenida deben estar entre 1 y 90");
        }
        this.intakeAlertHours = intakeAlertHours;
        this.cashTolerance = cashTolerance;
        this.stalledDays = stalledDays;
    }

    public boolean isSuspended() { return status == WorkshopStatus.SUSPENDED; }

    public String getName() { return name; }
    public String getDocumentNumber() { return documentNumber; }
    public String getAddress() { return address; }
    public String getMunicipality() { return municipality; }
    public String getPhone() { return phone; }
    public WorkshopStatus getStatus() { return status; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public Instant getSuspendedAt() { return suspendedAt; }
    public int getIntakeAlertHours() { return intakeAlertHours; }
    public long getCashTolerance() { return cashTolerance; }
    public int getStalledDays() { return stalledDays; }
    public long getVersion() { return version; }
}
