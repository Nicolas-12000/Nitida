package co.nitida.orders.domain;

import co.nitida.shared.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(schema = "orders", name = "work_order_photo")
public class OrderPhoto extends AssignedIdEntity {
    public enum Moment { BEFORE, AFTER }

    @Column(name = "file_id", nullable = false)
    private UUID fileId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Moment moment;

    protected OrderPhoto() {}

    public OrderPhoto(UUID id, UUID fileId, Moment moment) {
        super(id);
        this.fileId = fileId;
        this.moment = moment;
    }

    public UUID getFileId() { return fileId; }
    public Moment getMoment() { return moment; }
}
