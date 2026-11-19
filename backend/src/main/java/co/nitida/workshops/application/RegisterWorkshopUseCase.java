package co.nitida.workshops.application;

import co.nitida.identity.IdentityApi;
import co.nitida.shared.Role;
import co.nitida.shared.UuidV7;
import co.nitida.shared.audit.AuditService;
import co.nitida.shared.crypto.Ed25519;
import co.nitida.workshops.domain.SigningKey;
import co.nitida.workshops.domain.SigningKeyRepository;
import co.nitida.workshops.domain.Workshop;
import co.nitida.workshops.domain.WorkshopRepository;
import co.nitida.workshops.infrastructure.KeyCipher;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterWorkshopUseCase {
    public record Command(UUID userId, String name, String documentNumber, String address, String municipality,
                          String phone, UUID facadePhotoId) {}

    public record Result(UUID workshopId) {}

    private final WorkshopRepository workshops;
    private final SigningKeyRepository keys;
    private final KeyCipher cipher;
    private final IdentityApi identity;
    private final AuditService audit;
    private final Clock clock;

    RegisterWorkshopUseCase(WorkshopRepository workshops, SigningKeyRepository keys, KeyCipher cipher,
                            IdentityApi identity, AuditService audit, Clock clock) {
        this.workshops = workshops;
        this.keys = keys;
        this.cipher = cipher;
        this.identity = identity;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public Result execute(Command c) {
        var now = clock.instant();
        var workshop = workshops.save(Workshop.register(UuidV7.next(), c.name(), c.documentNumber(), c.address(),
                c.municipality(), c.phone(), c.facadePhotoId(), now));
        var pair = Ed25519.generate();
        var sealed = cipher.seal(pair.getPrivate().getEncoded(), workshop.getId());
        keys.save(new SigningKey(UuidV7.next(), workshop.getId(), pair.getPublic().getEncoded(),
                sealed.ciphertext(), sealed.nonce(), cipher.version(), now));
        identity.grant(c.userId(), workshop.getId(), Role.WORKSHOP_OWNER);
        audit.record(workshop.getId(), c.userId(), "WORKSHOP_REGISTERED", "workshop", workshop.getId(), null);
        return new Result(workshop.getId());
    }
}
