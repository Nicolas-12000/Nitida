package co.nitida.history.application;

import co.nitida.history.domain.EntryContent;
import co.nitida.history.domain.EntryType;
import co.nitida.motorcycles.MotorcycleRegistered;
import co.nitida.motorcycles.OwnershipTransferred;
import co.nitida.shared.events.OrderDelivered;
import co.nitida.workshops.WorkshopsApi;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-12: turns delivery and registration events into signed entries. Idempotent by source (RN-64). */
@Service
public class RecordHistoryEntryUseCase {
    private final HistoryAppender appender;
    private final co.nitida.history.domain.HistoryEntryRepository entries;
    private final WorkshopsApi workshops;

    RecordHistoryEntryUseCase(HistoryAppender appender, co.nitida.history.domain.HistoryEntryRepository entries, WorkshopsApi workshops) {
        this.appender = appender;
        this.entries = entries;
        this.workshops = workshops;
    }

    @Transactional
    public void execute(OrderDelivered e) {
        String source = "ORDER:" + e.orderId();
        if (entries.existsBySource(source)) return;
        boolean verified = verified(e.workshopId());
        appender.append(e.motorcycleId(), EntryType.SERVICE, e.workshopId(), source,
                EntryContent.service(e.motorcycleId(), verified, e));
    }

    @Transactional
    public void execute(MotorcycleRegistered e) {
        String source = "MOTORCYCLE:" + e.motorcycleId();
        if (entries.existsBySource(source)) return;
        appender.append(e.motorcycleId(), EntryType.INITIAL_REGISTRATION, e.workshopId(), source,
                EntryContent.initialRegistration(e.motorcycleId(), e.workshopId(), verified(e.workshopId()), e.declaredMileage(), e.occurredAt()));
    }

    /** RN-82: attested by the platform's own key, since no workshop performed it. */
    @Transactional
    public void execute(OwnershipTransferred e) {
        String source = "TRANSFER:" + e.transferId();
        if (entries.existsBySource(source)) return;
        appender.append(e.motorcycleId(), EntryType.OWNERSHIP_TRANSFER, WorkshopsApi.PLATFORM_ID, source,
                EntryContent.ownershipTransfer(e.motorcycleId(), WorkshopsApi.PLATFORM_ID, e.occurredAt(), e.transferId()));
    }

    /** RN-03/RN-65: only entries of VERIFIED workshops at that moment are marked verified. */
    private boolean verified(UUID workshopId) {
        return workshops.find(workshopId).map(WorkshopsApi.WorkshopInfo::verified).orElse(false);
    }
}
