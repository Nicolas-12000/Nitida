package co.nitida.cashdesk.application;

import co.nitida.cashdesk.domain.AlertType;
import co.nitida.catalog.SerialRejected;
import co.nitida.orders.WorkOrderCancelled;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Async, after-commit, new transaction; retried by the event registry and idempotent through the dedupe key. */
@Component
class CashdeskListeners {
    private final LeakAlerts alerts;

    CashdeskListeners(LeakAlerts alerts) {
        this.alerts = alerts;
    }

    @ApplicationModuleListener
    void on(WorkOrderCancelled e) {
        if (!e.hadParts()) return;
        alerts.raise(e.workshopId(), AlertType.F2_CANCELLED_WITH_PARTS, e.orderId(), e.orderId().toString(),
                "Orden cancelada con repuestos registrados");
    }

    /** RN-94: only "this workshop tried a serial already active elsewhere"; never which motorcycle or workshop. */
    @ApplicationModuleListener
    void on(SerialRejected e) {
        alerts.raise(e.workshopId(), AlertType.F6_DUPLICATE_SERIAL, e.orderId(), e.orderId() + ":" + e.serialHash(),
                "Se intentó registrar un serial que ya está activo en otra moto");
    }
}
