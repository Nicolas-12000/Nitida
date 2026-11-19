package co.nitida.motorcycles.domain;

import co.nitida.shared.ApiException;
import co.nitida.shared.Emails;
import co.nitida.shared.PhoneNumbers;
import co.nitida.shared.Texts;
import co.nitida.shared.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Personal data (Ley 1581). Lives only here, per workshop; never reaches the history (RN-22). */
@Entity
@Table(schema = "motorcycles", name = "customer")
public class Customer extends AssignedIdEntity {
    public static final String ERASED_NAME = "Cliente eliminado";

    @Column(name = "workshop_id", nullable = false)
    private UUID workshopId;
    @Column(name = "full_name", nullable = false)
    private String fullName;
    @Column(nullable = false)
    private String phone;
    @Column(name = "national_id")
    private String nationalId;
    private String email;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "erased_at")
    private Instant erasedAt;

    protected Customer() {}

    /** RN-20: name and phone required; national id and email optional. */
    public static Customer create(UUID id, UUID workshopId, String fullName, String phone, String nationalId, String email, Instant now) {
        var c = new Customer();
        c.id = id;
        c.workshopId = workshopId;
        c.createdAt = now;
        c.apply(fullName, phone, nationalId, email);
        return c;
    }

    public void apply(String fullName, String phone, String nationalId, String email) {
        if (erasedAt != null) throw ApiException.notFound();
        this.fullName = Texts.requireLength(fullName, 2, 120, "INVALID_NAME", "El nombre debe tener entre 2 y 120 caracteres");
        this.phone = PhoneNumbers.normalize(phone);
        String nid = Texts.blankToNull(nationalId == null ? null : nationalId.replaceAll("[\\s.]", ""));
        if (nid != null && !nid.matches("^[0-9]{5,12}$")) {
            throw ApiException.unprocessable("INVALID_NATIONAL_ID", "La cédula no es válida");
        }
        this.nationalId = nid;
        this.email = Texts.blankToNull(email) == null ? null : Emails.normalize(email);
    }

    /** RN-23: anonymize; the history stays intact because it never held these data. */
    public void erase(Instant now) {
        fullName = ERASED_NAME;
        phone = "+570000000000";
        nationalId = null;
        email = null;
        erasedAt = now;
    }

    public UUID getWorkshopId() { return workshopId; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public String getNationalId() { return nationalId; }
    public String getEmail() { return email; }
    public boolean isErased() { return erasedAt != null; }
}
