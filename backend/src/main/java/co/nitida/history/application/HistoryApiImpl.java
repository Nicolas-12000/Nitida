package co.nitida.history.application;

import co.nitida.history.HistoryApi;
import co.nitida.history.VerificationResult;
import co.nitida.history.domain.ChainHead;
import co.nitida.history.domain.ChainHeadRepository;
import co.nitida.history.domain.ChainVerifier;
import co.nitida.history.domain.EntryType;
import co.nitida.history.domain.HistoryEntry;
import co.nitida.history.domain.HistoryEntryRepository;
import co.nitida.shared.Json;
import co.nitida.shared.Mileage;
import co.nitida.workshops.WorkshopsApi;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class HistoryApiImpl implements HistoryApi {
    private static final Logger log = LoggerFactory.getLogger(HistoryApiImpl.class);
    private static final EnumSet<EntryType> WITH_MILEAGE = EnumSet.of(EntryType.INITIAL_REGISTRATION, EntryType.SERVICE, EntryType.ODOMETER_CHANGE);

    private final HistoryEntryRepository entries;
    private final ChainHeadRepository heads;
    private final WorkshopsApi workshops;

    HistoryApiImpl(HistoryEntryRepository entries, ChainHeadRepository heads, WorkshopsApi workshops) {
        this.entries = entries;
        this.heads = heads;
        this.workshops = workshops;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Mileage> lastMileage(UUID motorcycleId) {
        return entries.findFirstByMotorcycleIdAndTypeInOrderBySeqDesc(motorcycleId, WITH_MILEAGE).map(e -> {
            var content = (java.util.Map<?, ?>) Json.mapper().readValue(e.getPublicContent(), Object.class);
            return new Mileage(((Number) content.get("mileage")).intValue());
        });
    }

    @Override
    @Transactional(readOnly = true)
    public VerificationResult verify(UUID motorcycleId) {
        return verify(motorcycleId, entries.findByMotorcycleIdOrderBySeqAsc(motorcycleId));
    }

    /** Verifies an already-loaded chain; any failure is UNVERIFIABLE and an INVALID result raises an alert-level log (A09). */
    VerificationResult verify(UUID motorcycleId, List<HistoryEntry> chain) {
        try {
            ChainHead head = heads.findById(motorcycleId).orElse(null);
            var keys = new java.util.HashMap<UUID, Optional<byte[]>>();
            var result = ChainVerifier.verify(motorcycleId, chain.stream().map(HistoryEntry::toLink).toList(),
                    head == null ? null : new ChainVerifier.Head(head.getLastSeq(), head.getLastHash()),
                    keyId -> keys.computeIfAbsent(keyId, workshops::publicKey));
            if (result.status() == VerificationResult.Status.INVALID) {
                log.error("SECURITY chain verification INVALID motorcycle={} seq={} reason={}", motorcycleId, result.seq(), result.reason());
            }
            return result;
        } catch (RuntimeException e) {
            log.warn("chain verification could not be completed for motorcycle={}", motorcycleId, e);
            return VerificationResult.unverifiable("verification could not be completed");
        }
    }
}
