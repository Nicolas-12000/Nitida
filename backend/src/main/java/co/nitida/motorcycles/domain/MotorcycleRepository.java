package co.nitida.motorcycles.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MotorcycleRepository extends JpaRepository<Motorcycle, UUID> {
    Optional<Motorcycle> findByVin(String vin);

    Optional<Motorcycle> findByCurrentPlate(String plate);
}
