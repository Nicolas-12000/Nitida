package co.nitida.orders.domain;

import co.nitida.orders.OrderStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, UUID> {
    Optional<WorkOrder> findByIdAndWorkshopId(UUID id, UUID workshopId);

    List<WorkOrder> findByWorkshopIdAndStatusInOrderByCreatedAtDesc(UUID workshopId, Collection<OrderStatus> statuses, Pageable page);

    List<WorkOrder> findByStatusInAndUpdatedAtBefore(Collection<OrderStatus> statuses, Instant cutoff);
}
