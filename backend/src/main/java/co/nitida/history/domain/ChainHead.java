package co.nitida.history.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

@Entity
@Table(schema = "history", name = "chain_head")
public class ChainHead {
    @Id
    @Column(name = "motorcycle_id")
    private UUID motorcycleId;
    @Column(name = "last_seq", nullable = false)
    private long lastSeq;
    @Column(name = "last_hash", nullable = false)
    private byte[] lastHash;
    @Version
    private Long version;

    protected ChainHead() {}

    public static ChainHead genesis(UUID motorcycleId) {
        var h = new ChainHead();
        h.motorcycleId = motorcycleId;
        h.lastSeq = 0;
        h.lastHash = ChainSealer.GENESIS_HASH;
        return h;
    }

    public void advance(long seq, byte[] hash) {
        lastSeq = seq;
        lastHash = hash;
    }

    public long getLastSeq() { return lastSeq; }
    public byte[] getLastHash() { return lastHash; }
}
