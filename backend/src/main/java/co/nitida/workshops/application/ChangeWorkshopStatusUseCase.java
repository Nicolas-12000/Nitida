package co.nitida.workshops.application;

import co.nitida.identity.IdentityApi;
import co.nitida.shared.ApiException;
import co.nitida.shared.Json;
import co.nitida.shared.Texts;
import co.nitida.shared.audit.AuditService;
import co.nitida.workshops.WorkshopStatus;
import co.nitida.workshops.domain.WorkshopRepository;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RN-02: only PLATFORM_ADMIN verifies or suspends, and it is audited with who, when and evidence. */
@Service
public class ChangeWorkshopStatusUseCase {
    public record Command(UUID adminUserId, UUID workshopId, WorkshopStatus target, String evidence) {}

    private final WorkshopRepository workshops;
    private final IdentityApi identity;
    private final AuditService audit;
    private final Clock clock;

    ChangeWorkshopStatusUseCase(WorkshopRepository workshops, IdentityApi identity, AuditService audit, Clock clock) {
        this.workshops = workshops;
        this.identity = identity;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public WorkshopStatus execute(Command c) {
        if (!identity.isPlatformAdmin(c.adminUserId())) throw ApiException.forbidden();
        if (c.target() == null) throw ApiException.unprocessable("INVALID_STATUS", "Indique el estado");
        String evidence = Texts.requireLength(c.evidence(), 10, 500, "EVIDENCE_REQUIRED",
                "Describa la evidencia (llamada, visita) en 10 a 500 caracteres");
        var workshop = workshops.findById(c.workshopId()).orElseThrow(ApiException::notFound);
        workshop.changeStatus(c.target(), c.adminUserId(), clock.instant());
        audit.record(workshop.getId(), c.adminUserId(), "WORKSHOP_STATUS_CHANGED", "workshop", workshop.getId(),
                Json.of(Map.of("status", c.target().name(), "evidence", evidence)));
        return workshop.getStatus();
    }
}
