package co.nitida.motorcycles.domain;

import co.nitida.shared.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Holds the motorcycle's link to a user (no names ever reach the history: RN-24). */
@Entity
@Table(schema = "motorcycles", name = "vehicle_owner")
public class VehicleOwner extends AssignedIdEntity {
    @Column(name = "motorcycle_id", nullable = false)
    private UUID motorcycleId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;
    @Column(name = "valid_to")
    private Instant validTo;
    /** Equals the motorcycle id while open: the unique constraint allows one active owner (RN-81). */
    @Column(name = "active_motorcycle", unique = true)
    private UUID activeMotorcycle;

    protected VehicleOwner() {}

    public VehicleOwner(UUID id, UUID motorcycleId, UUID userId, Instant now) {
        super(id);
        this.motorcycleId = motorcycleId;
        this.userId = userId;
        this.validFrom = now;
        this.activeMotorcycle = motorcycleId;
    }

    public void close(Instant now) {
        validTo = now;
        activeMotorcycle = null;
    }

    public UUID getMotorcycleId() { return motorcycleId; }
    public UUID getUserId() { return userId; }
    public boolean isActive() { return validTo == null; }
}
