package co.nitida.history.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChainHeadRepository extends JpaRepository<ChainHead, UUID> {
    /** SELECT ... FOR UPDATE: appends to one motorcycle's chain are serialized (concurrent deliveries get seq 7 and 8). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from ChainHead h where h.motorcycleId = :id")
    Optional<ChainHead> lockById(@Param("id") UUID motorcycleId);
}
