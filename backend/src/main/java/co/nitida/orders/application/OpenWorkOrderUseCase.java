package co.nitida.orders.application;

import co.nitida.history.HistoryApi;
import co.nitida.intake.IntakeApi;
import co.nitida.motorcycles.MotorcyclesApi;
import co.nitida.orders.domain.WorkOrder;
import co.nitida.orders.domain.WorkOrderRepository;
import co.nitida.orders.OrderStatus;
import co.nitida.shared.ApiException;
import co.nitida.shared.Mileage;
import co.nitida.shared.Role;
import co.nitida.shared.UuidV7;
import co.nitida.shared.WorkshopContext;
import co.nitida.workshops.WorkshopsApi;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-07: opens the order from an open intake, validating mileage against the chain (RN-45). */
@Service
public class OpenWorkOrderUseCase {
    public record Command(WorkshopContext ctx, UUID intakeId, UUID customerId, String diagnosis, UUID mechanicId, UUID warrantyOfOrderId) {}

    public record Result(UUID orderId, UUID motorcycleId, int mileage) {}

    private final WorkOrderRepository orders;
    private final IntakeApi intakes;
    private final MotorcyclesApi motorcycles;
    private final HistoryApi history;
    private final WorkshopsApi workshops;
    private final Clock clock;

    OpenWorkOrderUseCase(WorkOrderRepository orders, IntakeApi intakes, MotorcyclesApi motorcycles, HistoryApi history,
                         WorkshopsApi workshops, Clock clock) {
        this.orders = orders;
        this.intakes = intakes;
        this.motorcycles = motorcycles;
        this.history = history;
        this.workshops = workshops;
        this.clock = clock;
    }

    @Transactional
    public Result execute(Command c) {
        c.ctx().require(Role.WORKSHOP_OWNER, Role.MECHANIC);
        workshops.requireActive(c.ctx().workshopId());
        var intake = intakes.findOpen(c.intakeId(), c.ctx().workshopId()).orElseThrow(ApiException::notFound);
        if (c.customerId() != null && !motorcycles.customerBelongsTo(c.customerId(), c.ctx().workshopId())) {
            throw ApiException.notFound(); // someone else's customer looks like it does not exist
        }
        // RN-45: equal or higher than the last known reading; a regression needs an ODOMETER_CHANGE first.
        new Mileage(intake.mileage()).validateAgainst(history.lastMileage(intake.motorcycleId()).orElse(null));

        UUID mechanic = c.mechanicId() != null && c.ctx().has(Role.WORKSHOP_OWNER) ? c.mechanicId() : c.ctx().userId();
        var order = WorkOrder.open(UuidV7.next(), c.ctx().workshopId(), intake.id(), intake.motorcycleId(), c.customerId(),
                c.diagnosis(), intake.mileage(), mechanic, clock.instant());
        if (c.warrantyOfOrderId() != null) {
            var previous = orders.findByIdAndWorkshopId(c.warrantyOfOrderId(), c.ctx().workshopId()).orElseThrow(ApiException::notFound);
            if (!previous.getMotorcycleId().equals(order.getMotorcycleId()) || previous.getStatus() != OrderStatus.DELIVERED) {
                throw ApiException.unprocessable("INVALID_WARRANTY", "La garantía debe referirse a una orden entregada de la misma moto");
            }
            order.markAsWarranty(previous.getId());
        }
        orders.save(order);
        intakes.linkToOrder(intake.id(), c.ctx().workshopId(), order.getId()); // RN-31: closes the intake, same transaction
        return new Result(order.getId(), order.getMotorcycleId(), intake.mileage());
    }
}
