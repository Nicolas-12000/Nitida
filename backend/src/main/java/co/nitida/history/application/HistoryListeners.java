package co.nitida.history.application;

import co.nitida.motorcycles.MotorcycleRegistered;
import co.nitida.motorcycles.OwnershipTransferred;
import co.nitida.shared.events.OrderDelivered;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Async, after-commit, new transaction; the JDBC event registry retries failures. Idempotent (RULE). */
@Component
class HistoryListeners {
    private final RecordHistoryEntryUseCase record;

    HistoryListeners(RecordHistoryEntryUseCase record) {
        this.record = record;
    }

    @ApplicationModuleListener
    void on(OrderDelivered event) {
        record.execute(event);
    }

    @ApplicationModuleListener
    void on(MotorcycleRegistered event) {
        record.execute(event);
    }

    @ApplicationModuleListener
    void on(OwnershipTransferred event) {
        record.execute(event);
    }
}
