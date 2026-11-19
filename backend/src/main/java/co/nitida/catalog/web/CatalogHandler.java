package co.nitida.catalog.web;

import co.nitida.catalog.application.ProposeProductUseCase;
import co.nitida.catalog.application.RetireSerialUseCase;
import co.nitida.catalog.application.ScanPartUseCase;
import co.nitida.shared.WorkshopContext;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

@Component
class CatalogHandler {
    record ProposeRequest(String code, String name, String brand, String reference) {}

    record RetireRequest(String reason) {}

    private final ScanPartUseCase scan;
    private final ProposeProductUseCase propose;
    private final RetireSerialUseCase retire;

    CatalogHandler(ScanPartUseCase scan, ProposeProductUseCase propose, RetireSerialUseCase retire) {
        this.scan = scan;
        this.propose = propose;
        this.retire = retire;
    }

    ServerResponse scan(ServerRequest req) {
        var r = scan.execute(new ScanPartUseCase.Command(WorkshopContext.current(), req.pathVariable("code")));
        return ServerResponse.ok().body(Map.of("code", r.code(), "codeType", r.codeType(),
                "found", r.product().isPresent(), "product", r.product().map(p -> (Object) p).orElse(Map.of())));
    }

    ServerResponse propose(ServerRequest req) throws Exception {
        var b = req.body(ProposeRequest.class);
        return ServerResponse.status(HttpStatus.CREATED).body(propose.execute(
                new ProposeProductUseCase.Command(WorkshopContext.current(), b.code(), b.name(), b.brand(), b.reference())));
    }

    ServerResponse retire(ServerRequest req) throws Exception {
        retire.execute(new RetireSerialUseCase.Command(WorkshopContext.current(), UUID.fromString(req.pathVariable("id")),
                req.body(RetireRequest.class).reason()));
        return ServerResponse.noContent().build();
    }
}
