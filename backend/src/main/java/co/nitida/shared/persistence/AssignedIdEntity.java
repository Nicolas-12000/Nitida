package co.nitida.shared.persistence;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/** Base for entities whose UUID is assigned by the application: avoids a SELECT before every INSERT. */
@MappedSuperclass
public abstract class AssignedIdEntity implements Persistable<UUID> {
    @Id
    protected UUID id;

    @Transient
    private boolean isNew = true;

    protected AssignedIdEntity() {}

    protected AssignedIdEntity(UUID id) {
        this.id = id;
    }

    @Override
    public UUID getId() { return id; }

    @Override
    public boolean isNew() { return isNew; }

    @PostLoad
    @PostPersist
    void markNotNew() { this.isNew = false; }
}
