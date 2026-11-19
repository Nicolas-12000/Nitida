package co.nitida.files.domain;

import co.nitida.shared.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "files", name = "stored_file")
public class StoredFile extends AssignedIdEntity {
    @Column(name = "workshop_id", nullable = false)
    private UUID workshopId;
    @Column(name = "mime_type", nullable = false)
    private String mimeType;
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;
    @Column(nullable = false)
    private String sha256;
    @Column(nullable = false)
    private String path;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StoredFile() {}

    public StoredFile(UUID id, UUID workshopId, String mimeType, long sizeBytes, String sha256, String path, Instant now) {
        super(id);
        this.workshopId = workshopId;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.path = path;
        this.createdAt = now;
    }

    public UUID getWorkshopId() { return workshopId; }
    public String getMimeType() { return mimeType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getSha256() { return sha256; }
    public String getPath() { return path; }
}
