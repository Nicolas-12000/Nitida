package co.nitida.motorcycles.application;

import co.nitida.motorcycles.domain.CustomerRepository;
import co.nitida.shared.ApiException;
import co.nitida.shared.Role;
import co.nitida.shared.WorkshopContext;
import co.nitida.shared.audit.AuditService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RN-23: supports the right to erasure without touching any history entry. */
@Service
public class EraseCustomerUseCase {
    public record Command(WorkshopContext ctx, UUID customerId) {}

    private final CustomerRepository customers;
    private final AuditService audit;
    private final Clock clock;

    EraseCustomerUseCase(CustomerRepository customers, AuditService audit, Clock clock) {
        this.customers = customers;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public void execute(Command c) {
        c.ctx().require(Role.WORKSHOP_OWNER);
        var customer = customers.findByIdAndWorkshopId(c.customerId(), c.ctx().workshopId()).orElseThrow(ApiException::notFound);
        if (customer.isErased()) return; // idempotent
        customer.erase(clock.instant());
        audit.record(c.ctx().workshopId(), c.ctx().userId(), "CUSTOMER_ERASED", "customer", customer.getId(), null);
    }
}
