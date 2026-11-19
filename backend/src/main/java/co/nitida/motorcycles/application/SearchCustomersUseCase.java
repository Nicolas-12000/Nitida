package co.nitida.motorcycles.application;

import co.nitida.motorcycles.domain.Customer;
import co.nitida.motorcycles.domain.CustomerRepository;
import co.nitida.shared.ApiException;
import co.nitida.shared.WorkshopContext;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchCustomersUseCase {
    public record Command(WorkshopContext ctx, String query) {}

    private static final int LIMIT = 20;
    private final CustomerRepository customers;

    SearchCustomersUseCase(CustomerRepository customers) {
        this.customers = customers;
    }

    /** All roles may search (a mechanic must pick the customer when opening an order); results are capped. */
    @Transactional(readOnly = true)
    public List<Customer> execute(Command c) {
        String q = c.query() == null ? "" : c.query().replaceAll("[%_\\\\]", "").strip();
        if (q.length() < 2) throw ApiException.unprocessable("QUERY_TOO_SHORT", "Escriba al menos 2 caracteres para buscar");
        return customers.search(c.ctx().workshopId(), q, PageRequest.of(0, LIMIT));
    }
}
