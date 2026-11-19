package co.nitida.identity.domain;

import co.nitida.shared.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(schema = "identity", name = "membership")
@IdClass(MembershipId.class)
public class Membership {
    @Id
    @Column(name = "user_id")
    private UUID userId;
    @Id
    @Column(name = "workshop_id")
    private UUID workshopId;
    @Id
    @Enumerated(EnumType.STRING)
    private Role role;
    @Column(nullable = false)
    private boolean active = true;

    protected Membership() {}

    public Membership(UUID userId, UUID workshopId, Role role) {
        this.userId = userId;
        this.workshopId = workshopId;
        this.role = role;
    }

    public void activate() { active = true; }
    public void deactivate() { active = false; }

    public UUID getUserId() { return userId; }
    public UUID getWorkshopId() { return workshopId; }
    public Role getRole() { return role; }
    public boolean isActive() { return active; }
}
