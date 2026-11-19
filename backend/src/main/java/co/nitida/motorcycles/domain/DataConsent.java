package co.nitida.motorcycles.domain;

import co.nitida.shared.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** RN-21: proof of the data-treatment authorization (when, which policy, how, who recorded it). */
@Entity
@Table(schema = "motorcycles", name = "data_consent")
public class DataConsent extends AssignedIdEntity {
    public enum Channel { IN_PERSON, ON_SCREEN_SIGNATURE }

    @Column(name = "workshop_id", nullable = false)
    private UUID workshopId;
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    @Column(name = "policy_version", nullable = false)
    private String policyVersion;
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false)
    private Channel channel;
    @Column(name = "recorded_by", nullable = false)
    private UUID recordedBy;
    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected DataConsent() {}

    public DataConsent(UUID id, UUID workshopId, UUID customerId, String policyVersion, Channel channel, UUID recordedBy, Instant now) {
        super(id);
        this.workshopId = workshopId;
        this.customerId = customerId;
        this.policyVersion = policyVersion;
        this.channel = channel;
        this.recordedBy = recordedBy;
        this.recordedAt = now;
    }

    public String getPolicyVersion() { return policyVersion; }
    public Channel getChannel() { return channel; }
}
