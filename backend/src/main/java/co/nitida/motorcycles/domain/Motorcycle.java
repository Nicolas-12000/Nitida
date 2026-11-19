package co.nitida.motorcycles.domain;

import co.nitida.shared.ApiException;
import co.nitida.shared.Texts;
import co.nitida.shared.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.Year;
import java.util.UUID;

@Entity
@Table(schema = "motorcycles", name = "motorcycle")
public class Motorcycle extends AssignedIdEntity {
    @Column(unique = true)
    private String vin;
    @Column(name = "current_plate", unique = true)
    private String currentPlate;
    @Column(name = "legacy_plate_format", nullable = false)
    private boolean legacyPlateFormat;
    @Column(nullable = false)
    private String brand;
    @Column(nullable = false)
    private String line;
    @Column(name = "model_year")
    private Integer modelYear;
    @Column(name = "displacement_cc")
    private Integer displacementCc;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Motorcycle() {}

    /** RN-16: brand, line, year and displacement are declared data; only sanity ranges are checked. */
    public static Motorcycle create(UUID id, String plate, String vin, String brand, String line, Integer modelYear,
                                    Integer displacementCc, Instant now) {
        if (plate == null && vin == null) {
            throw ApiException.unprocessable("IDENTIFIER_REQUIRED", "Indique la placa o el VIN de la moto");
        }
        var m = new Motorcycle();
        m.id = id;
        if (plate != null) {
            var p = VehicleIdentifiers.plate(plate);
            m.currentPlate = p.value();
            m.legacyPlateFormat = p.legacy();
        }
        if (vin != null) m.vin = VehicleIdentifiers.vin(vin);
        m.brand = Texts.requireLength(brand, 2, 40, "INVALID_BRAND", "La marca debe tener entre 2 y 40 caracteres");
        m.line = Texts.requireLength(line, 1, 40, "INVALID_LINE", "La línea debe tener entre 1 y 40 caracteres");
        if (modelYear != null && (modelYear < 1950 || modelYear > Year.now().getValue() + 1)) {
            throw ApiException.unprocessable("INVALID_MODEL_YEAR", "El modelo (año) no es válido");
        }
        if (displacementCc != null && (displacementCc < 20 || displacementCc > 2500)) {
            throw ApiException.unprocessable("INVALID_DISPLACEMENT", "El cilindraje debe estar entre 20 y 2500 cc");
        }
        m.modelYear = modelYear;
        m.displacementCc = displacementCc;
        m.createdAt = now;
        return m;
    }

    /** RN-13: a VIN arriving for a plate-only motorcycle is associated, never duplicated. */
    public void associateVin(String normalizedVin) {
        if (vin == null) {
            vin = normalizedVin;
        } else if (!vin.equals(normalizedVin)) {
            throw ApiException.conflict("PLATE_VIN_MISMATCH", "Esa placa ya está registrada con otro VIN");
        }
    }

    /** RN-14: plate changes keep the motorcycle id; the caller records the plate history. */
    public void changePlate(String rawPlate) {
        var p = VehicleIdentifiers.plate(rawPlate);
        currentPlate = p.value();
        legacyPlateFormat = p.legacy();
    }

    public String getVin() { return vin; }
    public String getCurrentPlate() { return currentPlate; }
    public boolean isLegacyPlateFormat() { return legacyPlateFormat; }
    public String getBrand() { return brand; }
    public String getLine() { return line; }
    public Integer getModelYear() { return modelYear; }
    public Integer getDisplacementCc() { return displacementCc; }
}
