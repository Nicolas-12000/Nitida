package co.nitida.history.application;

import co.nitida.history.VerificationResult;
import co.nitida.history.domain.EntryType;
import co.nitida.history.domain.HistoryEntry;
import co.nitida.motorcycles.MotorcyclesApi;
import co.nitida.motorcycles.domain.VehicleIdentifiers;
import co.nitida.shared.Json;
import co.nitida.workshops.WorkshopsApi;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Builds the public read model: public content only, never prices, private content or personal data (RN-73). */
@Component
class HistoryViews {
    record MotorcycleSummary(String plate, String brand, String line, Integer modelYear, Integer displacementCc) {}

    record EntryView(long seq, String type, Instant recordedAt, String workshopName, String municipality,
                     boolean workshopVerified, Instant workshopSuspendedAt, Map<?, ?> content) {}

    record EvidenceSummary(int partsTotal, int partsWithEvidence) {}

    record HistoryView(MotorcycleSummary motorcycle, List<EntryView> entries, VerificationResult verification,
                       EvidenceSummary evidence, int transfers) {}

    private final MotorcyclesApi motorcycles;
    private final WorkshopsApi workshops;
    private final HistoryApiImpl history;
    private final co.nitida.history.domain.HistoryEntryRepository entries;

    HistoryViews(MotorcyclesApi motorcycles, WorkshopsApi workshops, HistoryApiImpl history,
                 co.nitida.history.domain.HistoryEntryRepository entries) {
        this.motorcycles = motorcycles;
        this.workshops = workshops;
        this.history = history;
        this.entries = entries;
    }

    HistoryView of(UUID motorcycleId) {
        var moto = motorcycles.find(motorcycleId).orElseThrow(co.nitida.shared.ApiException::notFound);
        List<HistoryEntry> chain = entries.findByMotorcycleIdOrderBySeqAsc(motorcycleId);
        var shops = workshops.findAll(chain.stream().map(HistoryEntry::getWorkshopId).distinct().toList());
        int total = 0, withEvidence = 0, transfers = 0;
        var views = new java.util.ArrayList<EntryView>();
        for (var e : chain) {
            var content = (Map<?, ?>) Json.mapper().readValue(e.getPublicContent(), Object.class);
            var shop = shops.get(e.getWorkshopId());
            if (e.getType() == EntryType.OWNERSHIP_TRANSFER) transfers++;
            if (e.getType() == EntryType.SERVICE && content.get("parts") instanceof List<?> parts) {
                for (Object p : parts) {
                    total++;
                    var pm = (Map<?, ?>) p;
                    if (!"MANUAL".equals(pm.get("evidenceLevel")) || Boolean.TRUE.equals(pm.get("hasPhotoEvidence"))) withEvidence++;
                }
            }
            views.add(new EntryView(e.getSeq(), e.getType().name(), e.getRecordedAt(),
                    shop == null ? null : shop.name(), shop == null ? null : shop.municipality(),
                    Boolean.TRUE.equals(content.get("workshopVerified")), shop == null ? null : shop.suspendedAt(), content));
        }
        return new HistoryView(new MotorcycleSummary(VehicleIdentifiers.maskPlate(moto.plate()), moto.brand(), moto.line(),
                moto.modelYear(), moto.displacementCc()), views, history.verify(motorcycleId, chain),
                new EvidenceSummary(total, withEvidence), transfers);
    }
}
