package co.nitida.motorcycles.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleOwnerRepository extends JpaRepository<VehicleOwner, UUID> {
    Optional<VehicleOwner> findByMotorcycleIdAndValidToIsNull(UUID motorcycleId);

    List<VehicleOwner> findByUserIdAndValidToIsNull(UUID userId);
}
