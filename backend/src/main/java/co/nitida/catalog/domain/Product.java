package co.nitida.catalog.domain;

import co.nitida.shared.Texts;
import co.nitida.shared.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "catalog", name = "product")
public class Product extends AssignedIdEntity {
    @Column(name = "workshop_id", nullable = false)
    private UUID workshopId;
    @Column(nullable = false)
    private String code;
    @Column(name = "code_type", nullable = false)
    private String codeType;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String brand;
    private String reference;
    @Column(nullable = false)
    private String status = "PROPOSED";
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Product() {}

    public static Product propose(UUID id, UUID workshopId, ProductCode code, String name, String brand, String reference, Instant now) {
        var p = new Product();
        p.id = id;
        p.workshopId = workshopId;
        p.code = code.value();
        p.codeType = code.type();
        p.name = Texts.requireLength(name, 2, 120, "INVALID_NAME", "El nombre debe tener entre 2 y 120 caracteres");
        p.brand = Texts.requireLength(brand, 2, 60, "INVALID_BRAND", "La marca debe tener entre 2 y 60 caracteres");
        p.reference = Texts.blankToNull(reference);
        p.createdAt = now;
        return p;
    }

    public String getCode() { return code; }
    public String getCodeType() { return codeType; }
    public String getName() { return name; }
    public String getBrand() { return brand; }
    public String getReference() { return reference; }
    public String getStatus() { return status; }
}
