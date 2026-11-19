package co.nitida.workshops.application;

import co.nitida.identity.IdentityApi;
import co.nitida.shared.ApiException;
import co.nitida.workshops.WorkshopStatus;
import co.nitida.workshops.domain.WorkshopRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin queue of workshops by status (default: pending verification). Platform admins only. */
@Service
public class ListWorkshopsForAdminUseCase {
    public record Item(UUID id, String name, String municipality, String documentNumber, String phone, WorkshopStatus status) {}

    private final WorkshopRepository workshops;
    private final IdentityApi identity;

    ListWorkshopsForAdminUseCase(WorkshopRepository workshops, IdentityApi identity) {
        this.workshops = workshops;
        this.identity = identity;
    }

    @Transactional(readOnly = true)
    public List<Item> execute(UUID adminUserId, WorkshopStatus status) {
        if (!identity.isPlatformAdmin(adminUserId)) throw ApiException.forbidden();
        return workshops.findByStatusOrderByCreatedAtAsc(status).stream()
                .map(w -> new Item(w.getId(), w.getName(), w.getMunicipality(), w.getDocumentNumber(), w.getPhone(), w.getStatus())).toList();
    }
}
