package co.nitida.orders.domain;

import co.nitida.shared.ApiException;
import co.nitida.shared.Texts;
import co.nitida.shared.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(schema = "orders", name = "job")
public class Job extends AssignedIdEntity {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobType type;
    @Column(nullable = false)
    private String description;

    protected Job() {}

    public Job(UUID id, JobType type, String description) {
        super(id);
        if (type == null) throw ApiException.unprocessable("INVALID_JOB", "Indique el tipo de trabajo");
        this.type = type;
        this.description = Texts.requirePublic(description, 3, 500, "INVALID_JOB", "La descripción del trabajo debe tener entre 3 y 500 caracteres");
    }

    public JobType getType() { return type; }
    public String getDescription() { return description; }
}
