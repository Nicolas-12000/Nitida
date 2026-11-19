package co.nitida.workshops.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SigningKeyRepository extends JpaRepository<SigningKey, UUID> {
    Optional<SigningKey> findFirstByWorkshopIdAndRetiredAtIsNullOrderByCreatedAtDesc(UUID workshopId);
}
