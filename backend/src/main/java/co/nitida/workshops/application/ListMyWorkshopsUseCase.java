package co.nitida.workshops.application;

import co.nitida.identity.IdentityApi;
import co.nitida.shared.Role;
import co.nitida.workshops.WorkshopStatus;
import co.nitida.workshops.domain.WorkshopRepository;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The workshops the user belongs to, with the roles held in each (one query for all workshops). */
@Service
public class ListMyWorkshopsUseCase {
    public record Item(UUID id, String name, String municipality, WorkshopStatus status, List<Role> roles) {}

    private final WorkshopRepository workshops;
    private final IdentityApi identity;

    ListMyWorkshopsUseCase(WorkshopRepository workshops, IdentityApi identity) {
        this.workshops = workshops;
        this.identity = identity;
    }

    @Transactional(readOnly = true)
    public List<Item> execute(UUID userId) {
        var rolesByWorkshop = identity.membershipsOf(userId).stream().collect(Collectors.groupingBy(
                IdentityApi.WorkshopRole::workshopId, Collectors.mapping(IdentityApi.WorkshopRole::role, Collectors.toList())));
        return workshops.findByIdIn(rolesByWorkshop.keySet()).stream()
                .map(w -> new Item(w.getId(), w.getName(), w.getMunicipality(), w.getStatus(), rolesByWorkshop.get(w.getId()))).toList();
    }
}
