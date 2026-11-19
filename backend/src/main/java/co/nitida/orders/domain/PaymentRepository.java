package co.nitida.orders.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByWorkOrderIdOrderByRecordedAtAsc(UUID workOrderId);

    Optional<Payment> findByIdAndWorkshopId(UUID id, UUID workshopId);

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.workshopId = :ws and p.method = :method "
            + "and p.voidedAt is null and p.recordedAt >= :from and p.recordedAt < :to")
    long sumByMethod(@Param("ws") UUID workshopId, @Param("method") PaymentMethod method,
                     @Param("from") Instant from, @Param("to") Instant to);
}
