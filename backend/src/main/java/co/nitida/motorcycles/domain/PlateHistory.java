package co.nitida.motorcycles.domain;

import co.nitida.shared.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "motorcycles", name = "plate_history")
public class PlateHistory extends AssignedIdEntity {
    @Column(name = "motorcycle_id", nullable = false)
    private UUID motorcycleId;
    @Column(nullable = false)
    private String plate;
    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;
    @Column(name = "valid_to")
    private Instant validTo;

    protected PlateHistory() {}

    public PlateHistory(UUID id, UUID motorcycleId, String plate, Instant validFrom) {
        super(id);
        this.motorcycleId = motorcycleId;
        this.plate = plate;
        this.validFrom = validFrom;
    }

    public void close(Instant now) { validTo = now; }

    public String getPlate() { return plate; }
    public Instant getValidTo() { return validTo; }
}
