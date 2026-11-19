package co.nitida.cashdesk.application;

import co.nitida.cashdesk.domain.AlertRules;
import co.nitida.cashdesk.domain.AlertType;
import co.nitida.intake.IntakeApi;
import co.nitida.orders.OrdersApi;
import co.nitida.workshops.WorkshopsApi;
import java.time.Clock;
import java.time.Duration;
import java.util.stream.Stream;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-19: every 15 minutes raises F1 (intake without order), F4 (zero total without warranty) and F5 (stalled order). */
@Service
public class EvaluateScheduledAlertsUseCase {
    private final IntakeApi intakes;
    private final OrdersApi orders;
    private final WorkshopsApi workshops;
    private final LeakAlerts alerts;
    private final Clock clock;

    EvaluateScheduledAlertsUseCase(IntakeApi intakes, OrdersApi orders, WorkshopsApi workshops, LeakAlerts alerts, Clock clock) {
        this.intakes = intakes;
        this.orders = orders;
        this.workshops = workshops;
        this.alerts = alerts;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${nitida.alerts.scan-interval:PT15M}", initialDelayString = "${nitida.alerts.scan-interval:PT15M}")
    @Transactional
    public void execute() {
        var now = clock.instant();
        // the smallest thresholds the settings allow (1 h, 1 day) bound the queries; each workshop's own value filters next
        var overdueIntakes = intakes.openCreatedBefore(now.minus(Duration.ofHours(1)));
        var stalled = orders.stalledBefore(now.minus(Duration.ofDays(1)));
        var settings = workshops.findAll(Stream.concat(overdueIntakes.stream().map(IntakeApi.IntakeInfo::workshopId),
                stalled.stream().map(OrdersApi.StalledOrder::workshopId)).distinct().toList());

        for (var i : overdueIntakes) {
            var ws = settings.get(i.workshopId());
            if (ws != null && AlertRules.intakeOverdue(i.createdAt(), now, ws.intakeAlertHours())) {
                alerts.raise(i.workshopId(), AlertType.F1_INTAKE_WITHOUT_ORDER, i.id(), i.id().toString(),
                        "Recepción abierta hace más de " + ws.intakeAlertHours() + " h sin orden de trabajo");
            }
        }
        for (var o : stalled) {
            var ws = settings.get(o.workshopId());
            if (ws != null && AlertRules.orderStalled(o.lastChangedAt(), now, ws.stalledDays())) {
                // keyed by the last change: an order that moves and stalls again raises a new alert
                alerts.raise(o.workshopId(), AlertType.F5_STALLED_ORDER, o.orderId(), o.orderId() + ":" + o.lastChangedAt().toEpochMilli(),
                        "Orden en estado " + o.status() + " sin cambios hace más de " + ws.stalledDays() + " días");
            }
        }
        for (var z : orders.inconsistentZeroTotalOrders()) {
            alerts.raise(z.workshopId(), AlertType.F4_ZERO_TOTAL_WITHOUT_WARRANTY, z.orderId(), z.orderId().toString(),
                    "Orden entregada con total 0 sin ser garantía");
        }
    }

}
