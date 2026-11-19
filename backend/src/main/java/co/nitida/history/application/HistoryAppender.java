package co.nitida.history.application;

import co.nitida.history.HistoryEntryRecorded;
import co.nitida.history.domain.ChainHead;
import co.nitida.history.domain.ChainHeadRepository;
import co.nitida.history.domain.ChainSealer;
import co.nitida.history.domain.EntryContent;
import co.nitida.history.domain.EntryType;
import co.nitida.history.domain.HistoryEntry;
import co.nitida.history.domain.HistoryEntryRepository;
import co.nitida.shared.UuidV7;
import co.nitida.workshops.WorkshopsApi;
import java.time.Clock;
import java.util.Base64;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Single place where an entry is appended: lock the chain head, seal (hash chain), sign, store, publish.
 * Must run inside the caller's transaction. Fail closed: if signing or storing fails nothing is written.
 */
@Component
class HistoryAppender {
    private final ChainHeadRepository heads;
    private final HistoryEntryRepository entries;
    private final WorkshopsApi workshops;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    HistoryAppender(ChainHeadRepository heads, HistoryEntryRepository entries, WorkshopsApi workshops,
                    ApplicationEventPublisher events, Clock clock) {
        this.heads = heads;
        this.entries = entries;
        this.workshops = workshops;
        this.events = events;
        this.clock = clock;
    }

    HistoryEntry append(UUID motorcycleId, EntryType type, UUID workshopId, String source, EntryContent content) {
        ChainHead head = heads.lockById(motorcycleId).orElse(null);
        if (head == null) {
            if (type != EntryType.INITIAL_REGISTRATION) {
                // The chain must start with INITIAL_REGISTRATION; the publication registry retries this later.
                throw new IllegalStateException("chain not started for motorcycle " + motorcycleId);
            }
            head = ChainHead.genesis(motorcycleId);
        }
        var sealed = ChainSealer.seal(head.getLastSeq(), head.getLastHash(), content.publicForSeq());
        var signed = workshops.sign(workshopId, sealed.hash());
        var entry = entries.save(new HistoryEntry(UuidV7.next(), motorcycleId, type, workshopId, source, sealed, content,
                signed.signature(), signed.keyId(), clock.instant()));
        head.advance(sealed.seq(), sealed.hash());
        heads.save(head);
        events.publishEvent(new HistoryEntryRecorded(motorcycleId, sealed.seq(), type.name(),
                Base64.getUrlEncoder().withoutPadding().encodeToString(sealed.hash()), workshopId));
        return entry;
    }
}
