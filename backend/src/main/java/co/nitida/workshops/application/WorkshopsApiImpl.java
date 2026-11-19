package co.nitida.workshops.application;

import co.nitida.shared.ApiException;
import co.nitida.shared.crypto.Ed25519;
import co.nitida.shared.crypto.KeyCipher;
import co.nitida.workshops.WorkshopsApi;
import co.nitida.workshops.domain.SigningKey;
import co.nitida.workshops.domain.SigningKeyRepository;
import co.nitida.workshops.domain.Workshop;
import co.nitida.workshops.domain.WorkshopRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class WorkshopsApiImpl implements WorkshopsApi {
    private final WorkshopRepository workshops;
    private final SigningKeyRepository keys;
    private final KeyCipher cipher;

    WorkshopsApiImpl(WorkshopRepository workshops, SigningKeyRepository keys, KeyCipher cipher) {
        this.workshops = workshops;
        this.keys = keys;
        this.cipher = cipher;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WorkshopInfo> find(UUID workshopId) {
        return workshops.findById(workshopId).map(WorkshopsApiImpl::info);
    }

    @Override
    @Transactional(readOnly = true)
    public WorkshopInfo requireActive(UUID workshopId) {
        Workshop w = workshops.findById(workshopId).orElseThrow(ApiException::notFound);
        if (w.isSuspended()) {
            throw ApiException.locked("WORKSHOP_SUSPENDED", "El taller está suspendido y no puede crear registros nuevos");
        }
        return info(w);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkshopInfo> all() {
        return workshops.findAll().stream().filter(w -> !PLATFORM_ID.equals(w.getId())).map(WorkshopsApiImpl::info).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Map<UUID, WorkshopInfo> findAll(java.util.Collection<UUID> ids) {
        return workshops.findByIdIn(ids).stream().collect(java.util.stream.Collectors.toMap(Workshop::getId, WorkshopsApiImpl::info));
    }

    @Override
    @Transactional(readOnly = true)
    public SignedHash sign(UUID workshopId, byte[] data) {
        SigningKey key = keys.findFirstByWorkshopIdAndRetiredAtIsNullOrderByCreatedAtDesc(workshopId)
                .orElseThrow(() -> new IllegalStateException("workshop has no active signing key"));
        byte[] pkcs8 = cipher.open(key.getEncryptedPrivateKey(), key.getNonce(), workshopId);
        return new SignedHash(key.getId(), Ed25519.sign(pkcs8, data));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<byte[]> publicKey(UUID keyId) {
        return keys.findById(keyId).map(SigningKey::getPublicKey);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PublicKeyInfo> publicKeys() {
        return keys.findAll().stream()
                .map(k -> new PublicKeyInfo(k.getId(), k.getWorkshopId(), k.getAlgorithm(), k.getPublicKey(), k.isRetired()))
                .toList();
    }

    static WorkshopInfo info(Workshop w) {
        return new WorkshopInfo(w.getId(), w.getName(), w.getMunicipality(), w.getStatus(), w.getVerifiedAt(),
                w.getSuspendedAt(), w.getIntakeAlertHours(), w.getCashTolerance(), w.getStalledDays());
    }
}
