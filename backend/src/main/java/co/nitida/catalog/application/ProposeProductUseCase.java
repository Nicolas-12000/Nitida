package co.nitida.catalog.application;

import co.nitida.catalog.CatalogApi;
import co.nitida.catalog.CatalogApi.ProductInfo;
import co.nitida.shared.Role;
import co.nitida.shared.WorkshopContext;
import org.springframework.stereotype.Service;

@Service
public class ProposeProductUseCase {
    public record Command(WorkshopContext ctx, String code, String name, String brand, String reference) {}

    private final CatalogApi catalog;

    ProposeProductUseCase(CatalogApi catalog) {
        this.catalog = catalog;
    }

    @org.springframework.transaction.annotation.Transactional
    public ProductInfo execute(Command c) {
        c.ctx().require(Role.WORKSHOP_OWNER, Role.MECHANIC);
        return catalog.proposeProduct(c.ctx().workshopId(), c.code(), c.name(), c.brand(), c.reference());
    }
}
