package co.nitida.motorcycles.web;

import co.nitida.motorcycles.MotorcyclesApi;
import co.nitida.motorcycles.application.CreateCustomerUseCase;
import co.nitida.motorcycles.application.EraseCustomerUseCase;
import co.nitida.motorcycles.application.SearchCustomersUseCase;
import co.nitida.motorcycles.application.UpdateCustomerUseCase;
import co.nitida.motorcycles.domain.Customer;
import co.nitida.motorcycles.domain.DataConsent;
import co.nitida.shared.ApiException;
import co.nitida.shared.WorkshopContext;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

@Component
class MotorcycleHandler {
    record CreateCustomerRequest(String fullName, String phone, String nationalId, String email,
                                 boolean consentAccepted, String policyVersion, DataConsent.Channel consentChannel) {}

    record UpdateCustomerRequest(String fullName, String phone, String nationalId, String email) {}

    record CustomerResponse(UUID id, String fullName, String phone, String nationalId, String email) {
        static CustomerResponse from(Customer c) {
            return new CustomerResponse(c.getId(), c.getFullName(), c.getPhone(), c.getNationalId(), c.getEmail());
        }
    }

    private final MotorcyclesApi motorcycles;
    private final CreateCustomerUseCase create;
    private final UpdateCustomerUseCase update;
    private final EraseCustomerUseCase erase;
    private final SearchCustomersUseCase search;

    MotorcycleHandler(MotorcyclesApi motorcycles, CreateCustomerUseCase create, UpdateCustomerUseCase update,
                      EraseCustomerUseCase erase, SearchCustomersUseCase search) {
        this.motorcycles = motorcycles;
        this.create = create;
        this.update = update;
        this.erase = erase;
        this.search = search;
    }

    ServerResponse lookup(ServerRequest req) {
        WorkshopContext.current();
        var found = motorcycles.lookup(req.param("plate").orElse(null), req.param("vin").orElse(null))
                .orElseThrow(ApiException::notFound);
        return ServerResponse.ok().body(found);
    }

    ServerResponse createCustomer(ServerRequest req) throws Exception {
        var b = req.body(CreateCustomerRequest.class);
        var result = create.execute(new CreateCustomerUseCase.Command(WorkshopContext.current(), b.fullName(), b.phone(),
                b.nationalId(), b.email(), b.consentAccepted(), b.policyVersion(), b.consentChannel()));
        return ServerResponse.status(HttpStatus.CREATED).body(result);
    }

    ServerResponse searchCustomers(ServerRequest req) {
        var found = search.execute(new SearchCustomersUseCase.Command(WorkshopContext.current(), req.param("q").orElse(null)));
        return ServerResponse.ok().body(found.stream().map(CustomerResponse::from).toList());
    }

    ServerResponse updateCustomer(ServerRequest req) throws Exception {
        var b = req.body(UpdateCustomerRequest.class);
        update.execute(new UpdateCustomerUseCase.Command(WorkshopContext.current(), UUID.fromString(req.pathVariable("id")),
                b.fullName(), b.phone(), b.nationalId(), b.email()));
        return ServerResponse.noContent().build();
    }

    ServerResponse eraseCustomer(ServerRequest req) {
        erase.execute(new EraseCustomerUseCase.Command(WorkshopContext.current(), UUID.fromString(req.pathVariable("id"))));
        return ServerResponse.noContent().build();
    }
}
