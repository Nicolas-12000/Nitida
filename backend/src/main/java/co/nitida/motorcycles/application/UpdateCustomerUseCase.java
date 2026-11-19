package co.nitida.motorcycles.application;

import co.nitida.motorcycles.domain.CustomerRepository;
import co.nitida.shared.ApiException;
import co.nitida.shared.Role;
import co.nitida.shared.WorkshopContext;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateCustomerUseCase {
    public record Command(WorkshopContext ctx, UUID customerId, String fullName, String phone, String nationalId, String email) {}

    private final CustomerRepository customers;

    UpdateCustomerUseCase(CustomerRepository customers) {
        this.customers = customers;
    }

    @Transactional
    public void execute(Command c) {
        c.ctx().require(Role.WORKSHOP_OWNER, Role.FRONT_DESK);
        var customer = customers.findByIdAndWorkshopId(c.customerId(), c.ctx().workshopId()).orElseThrow(ApiException::notFound);
        customer.apply(c.fullName(), c.phone(), c.nationalId(), c.email());
    }
}
