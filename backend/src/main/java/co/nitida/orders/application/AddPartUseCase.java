package co.nitida.orders.application;

import co.nitida.catalog.CatalogApi;
import co.nitida.files.FilesApi;
import co.nitida.orders.domain.Part;
import co.nitida.orders.domain.PartCondition;
import co.nitida.shared.Role;
import co.nitida.shared.UuidV7;
import co.nitida.shared.WorkshopContext;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-08 / UC-22: adds a part; a scanned product code is validated (RN-92) and proposed to the workshop catalog (RN-93). */
@Service
public class AddPartUseCase {
    public record Command(WorkshopContext ctx, UUID orderId, String name, String reference, String brand, PartCondition condition,
                          int quantity, long unitPrice, String productCode, UUID replacementPhotoId) {}

    public record Result(UUID partId, String evidenceLevel) {}

    private final OrderAccess access;
    private final CatalogApi catalog;
    private final FilesApi files;
    private final Clock clock;

    AddPartUseCase(OrderAccess access, CatalogApi catalog, FilesApi files, Clock clock) {
        this.access = access;
        this.catalog = catalog;
        this.files = files;
        this.clock = clock;
    }

    @Transactional
    public Result execute(Command c) {
        c.ctx().require(Role.WORKSHOP_OWNER, Role.MECHANIC);
        var order = access.load(c.orderId(), c.ctx());
        String code = null;
        String type = null;
        if (c.productCode() != null && !c.productCode().isBlank()) {
            var parsed = catalog.parseCode(c.productCode()); // 422 INVALID_PRODUCT_CODE on a bad reading
            code = parsed.code();
            type = parsed.type();
            if (catalog.findProduct(c.ctx().workshopId(), code).isEmpty()) {
                catalog.proposeProduct(c.ctx().workshopId(), code, c.name(), c.brand(), c.reference());
            }
        }
        if (c.replacementPhotoId() != null) files.requireOwned(c.replacementPhotoId(), c.ctx().workshopId());
        var part = new Part(UuidV7.next(), c.name(), c.reference(), c.brand(), c.condition(), c.quantity(), c.unitPrice(),
                code, type, c.replacementPhotoId());
        order.addPart(part, clock.instant());
        return new Result(part.getId(), part.getEvidenceLevel().name());
    }
}
