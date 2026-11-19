package co.nitida.motorcycles.application;

import co.nitida.motorcycles.domain.MotorcycleRepository;
import co.nitida.motorcycles.domain.VehicleOwner;
import co.nitida.motorcycles.domain.VehicleOwnerRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** "Mis motos": current and past ownerships of the user, newest first. */
@Service
public class ListMyMotorcyclesUseCase {
    public record OwnedMotorcycle(UUID motorcycleId, String plate, String brand, String line, Integer modelYear,
                                  boolean active, Instant since, Instant until) {}

    private final VehicleOwnerRepository owners;
    private final MotorcycleRepository motorcycles;

    ListMyMotorcyclesUseCase(VehicleOwnerRepository owners, MotorcycleRepository motorcycles) {
        this.owners = owners;
        this.motorcycles = motorcycles;
    }

    @Transactional(readOnly = true)
    public List<OwnedMotorcycle> execute(UUID userId) {
        var mine = owners.findByUserIdOrderByValidFromDesc(userId);
        var motos = motorcycles.findAllById(mine.stream().map(VehicleOwner::getMotorcycleId).distinct().toList()).stream()
                .collect(java.util.stream.Collectors.toMap(m -> m.getId(), m -> m));
        return mine.stream().map(o -> {
            var m = motos.get(o.getMotorcycleId());
            return new OwnedMotorcycle(o.getMotorcycleId(), m.getCurrentPlate(), m.getBrand(), m.getLine(), m.getModelYear(),
                    o.isActive(), o.getValidFrom(), o.getValidTo());
        }).toList();
    }
}
