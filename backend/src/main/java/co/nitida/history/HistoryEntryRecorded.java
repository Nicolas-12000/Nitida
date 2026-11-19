package co.nitida.history;

import java.util.UUID;

public record HistoryEntryRecorded(UUID motorcycleId, long seq, String type, String hash, UUID workshopId) {}
