package co.nitida.orders.domain;

import co.nitida.catalog.EvidenceLevel;
import co.nitida.shared.ApiException;
import co.nitida.shared.CopAmount;
import co.nitida.shared.Texts;
import co.nitida.shared.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(schema = "orders", name = "part")
public class Part extends AssignedIdEntity {
    @Column(nullable = false)
    private String name;
    private String reference;
    @Column(nullable = false)
    private String brand;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PartCondition condition;
    @Column(nullable = false)
    private int quantity;
    @Column(name = "unit_price", nullable = false)
    private long unitPrice;
    @Column(name = "product_code")
    private String productCode;
    @Column(name = "code_type")
    private String codeType;
    @Column(name = "serial_id")
    private UUID serialId;
    @Column(name = "serial_normalized")
    private String serialNormalized;
    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_level", nullable = false)
    private EvidenceLevel evidenceLevel = EvidenceLevel.MANUAL;
    @Column(name = "authenticity_photo_id")
    private UUID authenticityPhotoId;
    @Column(name = "authenticity_method")
    private String authenticityMethod;
    @Column(name = "replacement_photo_id")
    private UUID replacementPhotoId;

    protected Part() {}

    /** RN-43/RN-44: quantity 1..999, unit price 0..50.000.000. {@code productCode} was already validated by the catalog. */
    public Part(UUID id, String name, String reference, String brand, PartCondition condition, int quantity, long unitPrice,
                String productCode, String codeType, UUID replacementPhotoId) {
        super(id);
        this.name = Texts.requireLength(name, 2, 120, "INVALID_PART", "El nombre del repuesto debe tener entre 2 y 120 caracteres");
        this.reference = Texts.blankToNull(reference);
        this.brand = Texts.requireLength(brand, 1, 60, "INVALID_PART", "La marca del repuesto es obligatoria");
        if (condition == null) throw ApiException.unprocessable("INVALID_PART", "Indique la condición del repuesto (OEM, alterno o usado)");
        this.condition = condition;
        if (quantity < 1 || quantity > 999) throw ApiException.unprocessable("INVALID_QUANTITY", "La cantidad debe estar entre 1 y 999");
        this.quantity = quantity;
        this.unitPrice = CopAmount.line(unitPrice).value();
        this.productCode = productCode;
        this.codeType = codeType;
        this.replacementPhotoId = replacementPhotoId;
        recalculateEvidence();
    }

    public CopAmount lineTotal() {
        return new CopAmount(unitPrice).times(quantity);
    }

    /** RN-94: the serial was accepted by the global registry. */
    public void attachSerial(UUID serialId, String normalizedSerial) {
        this.serialId = serialId;
        this.serialNormalized = normalizedSerial;
        recalculateEvidence();
    }

    /** RN-91: authenticity needs a photo of the seal and the method used. The QR content is never stored or opened (RN-95). */
    public void attachAuthenticity(UUID photoId, String method) {
        if (photoId == null) throw ApiException.unprocessable("PHOTO_REQUIRED", "Se requiere la foto del sello de autenticidad");
        this.authenticityPhotoId = photoId;
        this.authenticityMethod = Texts.requireLength(method, 5, 200, "INVALID_METHOD",
                "Describa cómo se verificó (5 a 200 caracteres)");
        recalculateEvidence();
    }

    /** RN-91: always the highest level the present evidence allows. */
    private void recalculateEvidence() {
        evidenceLevel = EvidenceLevel.compute(productCode != null, serialId != null, authenticityPhotoId != null);
    }

    public String getName() { return name; }
    public String getReference() { return reference; }
    public String getBrand() { return brand; }
    public PartCondition getCondition() { return condition; }
    public int getQuantity() { return quantity; }
    public long getUnitPrice() { return unitPrice; }
    public String getProductCode() { return productCode; }
    public String getCodeType() { return codeType; }
    public UUID getSerialId() { return serialId; }
    public String getSerialNormalized() { return serialNormalized; }
    public EvidenceLevel getEvidenceLevel() { return evidenceLevel; }
    public UUID getAuthenticityPhotoId() { return authenticityPhotoId; }
    public String getAuthenticityMethod() { return authenticityMethod; }
    public UUID getReplacementPhotoId() { return replacementPhotoId; }
}
