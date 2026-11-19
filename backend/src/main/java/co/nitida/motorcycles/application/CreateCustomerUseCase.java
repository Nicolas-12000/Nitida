package co.nitida.motorcycles.application;

import co.nitida.motorcycles.domain.Customer;
import co.nitida.motorcycles.domain.CustomerRepository;
import co.nitida.motorcycles.domain.DataConsent;
import co.nitida.motorcycles.domain.DataConsentRepository;
import co.nitida.shared.ApiException;
import co.nitida.shared.Role;
import co.nitida.shared.UuidV7;
import co.nitida.shared.WorkshopContext;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateCustomerUseCase {
    public record Command(WorkshopContext ctx, String fullName, String phone, String nationalId, String email,
                          boolean consentAccepted, String policyVersion, DataConsent.Channel channel) {}

    public record Result(UUID customerId) {}

    private final CustomerRepository customers;
    private final DataConsentRepository consents;
    private final Clock clock;
    private final String currentPolicy;

    CreateCustomerUseCase(CustomerRepository customers, DataConsentRepository consents, Clock clock,
                          @Value("${nitida.privacy.policy-version}") String currentPolicy) {
        this.customers = customers;
        this.consents = consents;
        this.clock = clock;
        this.currentPolicy = currentPolicy;
    }

    /** RN-21: without a recorded authorization the customer is not created. */
    @Transactional
    public Result execute(Command c) {
        c.ctx().require(Role.WORKSHOP_OWNER, Role.FRONT_DESK);
        if (!c.consentAccepted() || c.channel() == null) {
            throw ApiException.unprocessable("CONSENT_REQUIRED", "Se requiere la autorización de tratamiento de datos");
        }
        if (!currentPolicy.equals(c.policyVersion())) {
            throw ApiException.unprocessable("STALE_POLICY", "La versión de la política aceptada no es la vigente");
        }
        var now = clock.instant();
        var customer = customers.save(Customer.create(UuidV7.next(), c.ctx().workshopId(), c.fullName(), c.phone(),
                c.nationalId(), c.email(), now));
        consents.save(new DataConsent(UuidV7.next(), c.ctx().workshopId(), customer.getId(), c.policyVersion(),
                c.channel(), c.ctx().userId(), now));
        return new Result(customer.getId());
    }
}
