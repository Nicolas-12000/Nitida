package co.nitida.catalog.application;

import co.nitida.catalog.CatalogApi;
import co.nitida.catalog.CatalogApi.ProductInfo;
import co.nitida.catalog.domain.ProductCode;
import co.nitida.shared.Role;
import co.nitida.shared.WorkshopContext;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** UC-22: validate a scanned code and autocomplete from the workshop catalog (RN-92, RN-93). */
@Service
public class ScanPartUseCase {
    public record Command(WorkshopContext ctx, String code) {}

    public record Result(String code, String codeType, Optional<ProductInfo> product) {}

    private final CatalogApi catalog;

    ScanPartUseCase(CatalogApi catalog) {
        this.catalog = catalog;
    }

    public Result execute(Command c) {
        c.ctx().require(Role.WORKSHOP_OWNER, Role.MECHANIC);
        var code = ProductCode.of(c.code());
        return new Result(code.value(), code.type(), catalog.findProduct(c.ctx().workshopId(), code.value()));
    }
}
