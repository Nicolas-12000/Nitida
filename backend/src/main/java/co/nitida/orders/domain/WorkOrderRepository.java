package co.nitida.orders.domain;

import co.nitida.orders.OrderStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, UUID> {
    Optional<WorkOrder> findByIdAndWorkshopId(UUID id, UUID workshopId);

    List<WorkOrder> findByWorkshopIdAndStatusInOrderByCreatedAtDesc(UUID workshopId, Collection<OrderStatus> statuses, Pageable page);

    List<WorkOrder> findByStatusInAndUpdatedAtBefore(Collection<OrderStatus> statuses, Instant cutoff);

    @Query("""
            select o from WorkOrder o
            where o.status = co.nitida.orders.OrderStatus.DELIVERED and o.laborCost = 0 and o.warranty = false
              and not exists (select p.id from o.parts p where p.unitPrice > 0)""")
    List<WorkOrder> findDeliveredWithZeroTotalWithoutWarranty();
}
