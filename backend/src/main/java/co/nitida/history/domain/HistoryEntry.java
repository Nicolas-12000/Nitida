package co.nitida.history.domain;

import co.nitida.shared.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One link of a chain. Immutable by design: no setters, and the application never updates or deletes it. */
@Entity
@Table(schema = "history", name = "history_entry")
public class HistoryEntry extends AssignedIdEntity {
    @Column(name = "motorcycle_id", nullable = false, updatable = false)
    private UUID motorcycleId;
    @Column(nullable = false, updatable = false)
    private long seq;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private EntryType type;
    @Column(name = "workshop_id", nullable = false, updatable = false)
    private UUID workshopId;
    @Column(updatable = false)
    private String source;
    @Column(name = "public_content", nullable = false, updatable = false)
    private String publicContent;
    @Column(name = "private_content", nullable = false, updatable = false)
    private String privateContent;
    @Column(name = "private_hash", nullable = false, updatable = false)
    private String privateHash;
    @Column(name = "previous_hash", nullable = false, updatable = false)
    private byte[] previousHash;
    @Column(nullable = false, updatable = false)
    private byte[] hash;
    @Column(nullable = false, updatable = false)
    private byte[] signature;
    @Column(name = "key_id", nullable = false, updatable = false)
    private UUID keyId;
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected HistoryEntry() {}

    public HistoryEntry(UUID id, UUID motorcycleId, EntryType type, UUID workshopId, String source,
                        ChainSealer.Sealed sealed, EntryContent content, byte[] signature, UUID keyId, Instant now) {
        super(id);
        this.motorcycleId = motorcycleId;
        this.seq = sealed.seq();
        this.type = type;
        this.workshopId = workshopId;
        this.source = source;
        this.publicContent = sealed.canonicalPublicContent();
        this.privateContent = content.privateJson();
        this.privateHash = content.privateHash();
        this.previousHash = sealed.previousHash();
        this.hash = sealed.hash();
        this.signature = signature;
        this.keyId = keyId;
        this.recordedAt = now;
    }

    public ChainVerifier.Link toLink() {
        return new ChainVerifier.Link(motorcycleId, seq, publicContent, previousHash, hash, signature, keyId);
    }

    public UUID getMotorcycleId() { return motorcycleId; }
    public long getSeq() { return seq; }
    public EntryType getType() { return type; }
    public UUID getWorkshopId() { return workshopId; }
    public String getPublicContent() { return publicContent; }
    public String getPrivateContent() { return privateContent; }
    public String getPrivateHash() { return privateHash; }
    public byte[] getHash() { return hash; }
    public UUID getKeyId() { return keyId; }
    public Instant getRecordedAt() { return recordedAt; }
}
