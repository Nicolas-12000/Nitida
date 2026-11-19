package co.nitida.workshops.domain;

import co.nitida.shared.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "workshops", name = "signing_key")
public class SigningKey extends AssignedIdEntity {
    @Column(name = "workshop_id", nullable = false)
    private UUID workshopId;
    @Column(nullable = false)
    private String algorithm = "Ed25519";
    @Column(name = "public_key", nullable = false)
    private byte[] publicKey;
    @Column(name = "encrypted_private_key", nullable = false)
    private byte[] encryptedPrivateKey;
    @Column(nullable = false)
    private byte[] nonce;
    @Column(name = "kek_version", nullable = false)
    private int kekVersion;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "retired_at")
    private Instant retiredAt;

    protected SigningKey() {}

    public SigningKey(UUID id, UUID workshopId, byte[] publicKey, byte[] encryptedPrivateKey, byte[] nonce, int kekVersion, Instant now) {
        super(id);
        this.workshopId = workshopId;
        this.publicKey = publicKey;
        this.encryptedPrivateKey = encryptedPrivateKey;
        this.nonce = nonce;
        this.kekVersion = kekVersion;
        this.createdAt = now;
    }

    public void retire(Instant now) { retiredAt = now; }

    public UUID getWorkshopId() { return workshopId; }
    public String getAlgorithm() { return algorithm; }
    public byte[] getPublicKey() { return publicKey; }
    public byte[] getEncryptedPrivateKey() { return encryptedPrivateKey; }
    public byte[] getNonce() { return nonce; }
    public int getKekVersion() { return kekVersion; }
    public boolean isRetired() { return retiredAt != null; }
}
