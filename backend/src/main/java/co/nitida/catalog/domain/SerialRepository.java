package co.nitida.catalog.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SerialRepository extends JpaRepository<Serial, UUID> {
    @org.springframework.data.jpa.repository.Query("select s from Serial s where s.normalizedSerial = :n and s.status = 'ACTIVE'")
    Optional<Serial> findActive(@org.springframework.data.repository.query.Param("n") String normalized);

    Optional<Serial> findFirstByNormalizedSerialAndStatusOrderByRegisteredAtDesc(String normalized, String status);

    List<Serial> findByWorkOrderIdAndStatus(UUID workOrderId, String status);
}
