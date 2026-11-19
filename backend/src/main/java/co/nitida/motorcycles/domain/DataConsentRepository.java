package co.nitida.motorcycles.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataConsentRepository extends JpaRepository<DataConsent, UUID> {
    long countByCustomerId(UUID customerId);
}
