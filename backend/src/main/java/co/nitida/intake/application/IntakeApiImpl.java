package co.nitida.intake.application;

import co.nitida.intake.IntakeApi;
import co.nitida.intake.domain.Intake;
import co.nitida.intake.domain.IntakeRepository;
import co.nitida.intake.domain.IntakeStatus;
import co.nitida.shared.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class IntakeApiImpl implements IntakeApi {
    private final IntakeRepository intakes;

    IntakeApiImpl(IntakeRepository intakes) {
        this.intakes = intakes;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IntakeInfo> findOpen(UUID intakeId, UUID workshopId) {
        return intakes.findByIdAndWorkshopId(intakeId, workshopId).filter(i -> i.getStatus() == IntakeStatus.OPEN)
                .map(IntakeApiImpl::info);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IntakeInfo> open(UUID workshopId) {
        return intakes.findByWorkshopIdAndStatusOrderByCreatedAtAsc(workshopId, IntakeStatus.OPEN).stream().map(IntakeApiImpl::info).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasReceived(UUID workshopId, UUID motorcycleId) {
        return intakes.existsByWorkshopIdAndMotorcycleId(workshopId, motorcycleId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IntakeInfo> openCreatedBefore(Instant cutoff) {
        return intakes.findByStatusAndCreatedAtBefore(IntakeStatus.OPEN, cutoff).stream().map(IntakeApiImpl::info).toList();
    }

    @Override
    @Transactional
    public void linkToOrder(UUID intakeId, UUID workshopId, UUID workOrderId) {
        intakes.findByIdAndWorkshopId(intakeId, workshopId).orElseThrow(ApiException::notFound).linkToOrder(workOrderId);
    }

    static IntakeInfo info(Intake i) {
        return new IntakeInfo(i.getId(), i.getWorkshopId(), i.getMotorcycleId(), i.getMileage(), i.getCreatedAt());
    }
}
