package co.nitida.workshops.domain;

import co.nitida.workshops.WorkshopStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkshopRepository extends JpaRepository<Workshop, UUID> {
    List<Workshop> findByStatusOrderByCreatedAtAsc(WorkshopStatus status);

    List<Workshop> findByIdIn(Collection<UUID> ids);
}
