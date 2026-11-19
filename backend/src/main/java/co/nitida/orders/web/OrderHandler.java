package co.nitida.orders.web;

import co.nitida.orders.OrderStatus;
import co.nitida.orders.application.AddOrderPhotoUseCase;
import co.nitida.orders.application.AddPartUseCase;
import co.nitida.orders.application.ChangeWorkOrderStatusUseCase;
import co.nitida.orders.application.DeliverOrderUseCase;
import co.nitida.orders.application.EditWorkOrderUseCase;
import co.nitida.orders.application.OpenWorkOrderUseCase;
import co.nitida.orders.application.QueryWorkOrdersUseCase;
import co.nitida.orders.application.RecordAuthenticityCheckUseCase;
import co.nitida.orders.application.RegisterPaymentUseCase;
import co.nitida.orders.application.RegisterSerialUseCase;
import co.nitida.orders.application.RemovePartUseCase;
import co.nitida.orders.application.VoidPaymentUseCase;
import co.nitida.orders.domain.OrderPhoto;
import co.nitida.orders.domain.PartCondition;
import co.nitida.orders.domain.PaymentMethod;
import co.nitida.shared.Page;
import co.nitida.shared.WorkshopContext;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/** Thin: HTTP in, use case, HTTP out. No business rule lives here. */
@Component
class OrderHandler {
    record OpenRequest(UUID intakeId, UUID customerId, String diagnosis, UUID mechanicId, UUID warrantyOfOrderId) {}

    record EditRequest(long version, String diagnosis, Long laborCost, Integer warrantyDays, Integer warrantyKm,
                       List<EditWorkOrderUseCase.JobInput> jobs) {}

    record TransitionRequest(OrderStatus status, String reason, long version) {}

    record PartRequest(String name, String reference, String brand, PartCondition condition, int quantity, long unitPrice,
                       String productCode, UUID replacementPhotoId) {}

    record SerialRequest(String serial, String reactivationReason) {}

    record AuthenticityRequest(UUID photoId, String method) {}

    record PhotoRequest(UUID fileId, OrderPhoto.Moment moment) {}

    record PaymentRequest(PaymentMethod method, long amount) {}

    record VoidRequest(String reason) {}

    record DeliveryRequest(long version) {}

    private final OpenWorkOrderUseCase open;
    private final EditWorkOrderUseCase edit;
    private final ChangeWorkOrderStatusUseCase changeStatus;
    private final AddPartUseCase addPart;
    private final RemovePartUseCase removePart;
    private final RegisterSerialUseCase registerSerial;
    private final RecordAuthenticityCheckUseCase authenticity;
    private final AddOrderPhotoUseCase addPhoto;
    private final RegisterPaymentUseCase registerPayment;
    private final VoidPaymentUseCase voidPayment;
    private final DeliverOrderUseCase deliver;
    private final QueryWorkOrdersUseCase query;

    OrderHandler(OpenWorkOrderUseCase open, EditWorkOrderUseCase edit, ChangeWorkOrderStatusUseCase changeStatus,
                 AddPartUseCase addPart, RemovePartUseCase removePart, RegisterSerialUseCase registerSerial,
                 RecordAuthenticityCheckUseCase authenticity, AddOrderPhotoUseCase addPhoto, RegisterPaymentUseCase registerPayment,
                 VoidPaymentUseCase voidPayment, DeliverOrderUseCase deliver, QueryWorkOrdersUseCase query) {
        this.open = open;
        this.edit = edit;
        this.changeStatus = changeStatus;
        this.addPart = addPart;
        this.removePart = removePart;
        this.registerSerial = registerSerial;
        this.authenticity = authenticity;
        this.addPhoto = addPhoto;
        this.registerPayment = registerPayment;
        this.voidPayment = voidPayment;
        this.deliver = deliver;
        this.query = query;
    }

    private static UUID id(ServerRequest req) { return UUID.fromString(req.pathVariable("id")); }

    ServerResponse open(ServerRequest req) throws Exception {
        var b = req.body(OpenRequest.class);
        return ServerResponse.status(HttpStatus.CREATED).body(open.execute(new OpenWorkOrderUseCase.Command(
                WorkshopContext.current(), b.intakeId(), b.customerId(), b.diagnosis(), b.mechanicId(), b.warrantyOfOrderId())));
    }

    ServerResponse list(ServerRequest req) {
        var statuses = EnumSet.noneOf(OrderStatus.class);
        req.params().getOrDefault("status", List.of()).forEach(s -> statuses.add(OrderStatus.valueOf(s)));
        var page = query.list(WorkshopContext.current(), statuses, Page.limit(req.param("limit").orElse(null)), Page.position(req.param("cursor").orElse(null)));
        var ok = ServerResponse.ok();
        if (page.nextCursor() != null) ok.header(Page.HEADER, page.nextCursor());
        return ok.body(page.items());
    }

    ServerResponse detail(ServerRequest req) {
        return ServerResponse.ok().body(query.detail(WorkshopContext.current(), id(req)));
    }

    ServerResponse edit(ServerRequest req) throws Exception {
        var b = req.body(EditRequest.class);
        edit.execute(new EditWorkOrderUseCase.Command(WorkshopContext.current(), id(req), b.version(), b.diagnosis(),
                b.laborCost(), b.warrantyDays(), b.warrantyKm(), b.jobs()));
        return ServerResponse.noContent().build();
    }

    ServerResponse changeStatus(ServerRequest req) throws Exception {
        var b = req.body(TransitionRequest.class);
        var status = changeStatus.execute(new ChangeWorkOrderStatusUseCase.Command(WorkshopContext.current(), id(req), b.status(), b.reason(), b.version()));
        return ServerResponse.ok().body(Map.of("status", status));
    }

    ServerResponse addPart(ServerRequest req) throws Exception {
        var b = req.body(PartRequest.class);
        return ServerResponse.status(HttpStatus.CREATED).body(addPart.execute(new AddPartUseCase.Command(WorkshopContext.current(), id(req),
                b.name(), b.reference(), b.brand(), b.condition(), b.quantity(), b.unitPrice(), b.productCode(), b.replacementPhotoId())));
    }

    ServerResponse removePart(ServerRequest req) {
        removePart.execute(new RemovePartUseCase.Command(WorkshopContext.current(), id(req), UUID.fromString(req.pathVariable("partId"))));
        return ServerResponse.noContent().build();
    }

    ServerResponse registerSerial(ServerRequest req) throws Exception {
        var b = req.body(SerialRequest.class);
        return ServerResponse.ok().body(registerSerial.execute(new RegisterSerialUseCase.Command(WorkshopContext.current(), id(req),
                UUID.fromString(req.pathVariable("partId")), b.serial(), b.reactivationReason())));
    }

    ServerResponse authenticity(ServerRequest req) throws Exception {
        var b = req.body(AuthenticityRequest.class);
        String level = authenticity.execute(new RecordAuthenticityCheckUseCase.Command(WorkshopContext.current(), id(req),
                UUID.fromString(req.pathVariable("partId")), b.photoId(), b.method()));
        return ServerResponse.ok().body(Map.of("evidenceLevel", level));
    }

    ServerResponse addPhoto(ServerRequest req) throws Exception {
        var b = req.body(PhotoRequest.class);
        addPhoto.execute(new AddOrderPhotoUseCase.Command(WorkshopContext.current(), id(req), b.fileId(), b.moment()));
        return ServerResponse.noContent().build();
    }

    ServerResponse registerPayment(ServerRequest req) throws Exception {
        var b = req.body(PaymentRequest.class);
        UUID paymentId = registerPayment.execute(new RegisterPaymentUseCase.Command(WorkshopContext.current(), id(req), b.method(), b.amount()));
        return ServerResponse.status(HttpStatus.CREATED).body(Map.of("paymentId", paymentId));
    }

    ServerResponse voidPayment(ServerRequest req) throws Exception {
        voidPayment.execute(new VoidPaymentUseCase.Command(WorkshopContext.current(), id(req), req.body(VoidRequest.class).reason()));
        return ServerResponse.noContent().build();
    }

    ServerResponse deliver(ServerRequest req) throws Exception {
        var result = deliver.execute(new DeliverOrderUseCase.Command(WorkshopContext.current(), id(req), req.body(DeliveryRequest.class).version()));
        return ServerResponse.ok().header("Cache-Control", "no-store").body(Map.of("orderId", result.orderId(), "total", result.total().value(),
                "claimCode", result.claimCode(), "claimCodeExpiresAt", result.claimCodeExpiresAt()));
    }
}
