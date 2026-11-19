package co.nitida.motorcycles.application;

import co.nitida.motorcycles.MotorcycleRegistered;
import co.nitida.motorcycles.MotorcyclesApi;
import co.nitida.motorcycles.domain.ClaimCode;
import co.nitida.motorcycles.domain.ClaimCodeRepository;
import co.nitida.motorcycles.domain.CustomerRepository;
import co.nitida.motorcycles.domain.Motorcycle;
import co.nitida.motorcycles.domain.MotorcycleRepository;
import co.nitida.motorcycles.domain.PlateHistory;
import co.nitida.motorcycles.domain.PlateHistoryRepository;
import co.nitida.motorcycles.domain.VehicleIdentifiers;
import co.nitida.motorcycles.domain.VehicleOwnerRepository;
import co.nitida.shared.ApiException;
import co.nitida.shared.Mileage;
import co.nitida.shared.UuidV7;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MotorcyclesApiImpl implements MotorcyclesApi {
    private final MotorcycleRepository motorcycles;
    private final PlateHistoryRepository plates;
    private final CustomerRepository customers;
    private final ClaimCodeRepository claimCodes;
    private final VehicleOwnerRepository owners;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    MotorcyclesApiImpl(MotorcycleRepository motorcycles, PlateHistoryRepository plates, CustomerRepository customers,
                       ClaimCodeRepository claimCodes, VehicleOwnerRepository owners, ApplicationEventPublisher events, Clock clock) {
        this.motorcycles = motorcycles;
        this.plates = plates;
        this.customers = customers;
        this.claimCodes = claimCodes;
        this.owners = owners;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Registration findOrRegister(UUID workshopId, String plate, String vin, MotorcycleDetails details, int declaredMileage) {
        String normPlate = VehicleIdentifiers.normalize(plate);
        String normVin = VehicleIdentifiers.normalize(vin);
        if (normPlate == null && normVin == null) {
            throw ApiException.unprocessable("IDENTIFIER_REQUIRED", "Indique la placa o el VIN de la moto");
        }
        if (normPlate != null) VehicleIdentifiers.plate(normPlate); // validate even when the motorcycle exists
        if (normVin != null) VehicleIdentifiers.vin(normVin);
        var mileage = new Mileage(declaredMileage);

        // RN-13: VIN rules; RN-15: an existing motorcycle is returned, never duplicated or rejected.
        Optional<Motorcycle> byVin = normVin == null ? Optional.empty() : motorcycles.findByVin(normVin);
        if (byVin.isPresent()) return new Registration(info(byVin.get()), false);
        Optional<Motorcycle> byPlate = normPlate == null ? Optional.empty() : motorcycles.findByCurrentPlate(normPlate);
        if (byPlate.isPresent()) {
            var m = byPlate.get();
            if (normVin != null) m.associateVin(normVin);
            return new Registration(info(m), false);
        }
        if (details == null) throw ApiException.unprocessable("DETAILS_REQUIRED", "La moto es nueva: indique marca y línea");
        var now = clock.instant();
        var m = motorcycles.save(Motorcycle.create(UuidV7.next(), normPlate, normVin, details.brand(), details.line(),
                details.modelYear(), details.displacementCc(), now));
        if (m.getCurrentPlate() != null) plates.save(new PlateHistory(UuidV7.next(), m.getId(), m.getCurrentPlate(), now));
        events.publishEvent(new MotorcycleRegistered(m.getId(), mileage.km(), workshopId, now));
        return new Registration(info(m), true);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MotorcycleInfo> find(UUID motorcycleId) {
        return motorcycles.findById(motorcycleId).map(MotorcyclesApiImpl::info);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Map<UUID, MotorcycleInfo> findAll(java.util.Collection<UUID> ids) {
        return motorcycles.findAllById(ids).stream().collect(java.util.stream.Collectors.toMap(Motorcycle::getId, MotorcyclesApiImpl::info));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MotorcycleInfo> lookup(String plate, String vin) {
        String normVin = VehicleIdentifiers.normalize(vin);
        String normPlate = VehicleIdentifiers.normalize(plate);
        if (normVin != null) return motorcycles.findByVin(normVin).map(MotorcyclesApiImpl::info);
        if (normPlate != null) return motorcycles.findByCurrentPlate(normPlate).map(MotorcyclesApiImpl::info);
        return Optional.empty();
    }

    @Override
    @Transactional
    public void issueClaimCode(UUID motorcycleId, String codeHash, java.time.Instant expiresAt) {
        claimCodes.save(new ClaimCode(UuidV7.next(), motorcycleId, ClaimCode.DELIVERY, codeHash, null, expiresAt));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean customerBelongsTo(UUID customerId, UUID workshopId) {
        return customers.existsByIdAndWorkshopIdAndErasedAtIsNull(customerId, workshopId);
    }

    static MotorcycleInfo info(Motorcycle m) {
        return new MotorcycleInfo(m.getId(), m.getVin(), m.getCurrentPlate(), VehicleIdentifiers.maskPlate(m.getCurrentPlate()),
                m.isLegacyPlateFormat(), m.getBrand(),
                m.getLine(), m.getModelYear(), m.getDisplacementCc());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OwnershipPeriod> ownership(UUID userId, UUID motorcycleId) {
        return owners.findFirstByUserIdAndMotorcycleIdOrderByValidFromDesc(userId, motorcycleId)
                .map(o -> new OwnershipPeriod(o.getValidFrom(), o.getValidTo()));
    }
}
