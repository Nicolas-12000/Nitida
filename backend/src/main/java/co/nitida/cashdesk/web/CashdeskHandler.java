package co.nitida.cashdesk.web;

import co.nitida.cashdesk.application.CloseCashCountUseCase;
import co.nitida.cashdesk.application.GetCashCountUseCase;
import co.nitida.cashdesk.application.ListLeakAlertsUseCase;
import co.nitida.cashdesk.application.ReopenCashCountUseCase;
import co.nitida.cashdesk.application.ResolveLeakAlertUseCase;
import co.nitida.shared.ApiException;
import co.nitida.shared.WorkshopContext;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

@Component
class CashdeskHandler {
    record CloseRequest(Long counted) {}

    record ReasonRequest(String reason) {}

    record CommentRequest(String comment) {}

    private final GetCashCountUseCase get;
    private final CloseCashCountUseCase close;
    private final ReopenCashCountUseCase reopen;
    private final ListLeakAlertsUseCase list;
    private final ResolveLeakAlertUseCase resolve;

    CashdeskHandler(GetCashCountUseCase get, CloseCashCountUseCase close, ReopenCashCountUseCase reopen,
                    ListLeakAlertsUseCase list, ResolveLeakAlertUseCase resolve) {
        this.get = get;
        this.close = close;
        this.reopen = reopen;
        this.list = list;
        this.resolve = resolve;
    }

    ServerResponse count(ServerRequest req) {
        return ServerResponse.ok().body(get.execute(WorkshopContext.current(), day(req)));
    }

    ServerResponse close(ServerRequest req) throws Exception {
        var ctx = WorkshopContext.current();
        var b = req.body(CloseRequest.class);
        return ServerResponse.ok().body(close.execute(new CloseCashCountUseCase.Command(ctx, day(req), b.counted())));
    }

    ServerResponse reopen(ServerRequest req) throws Exception {
        var ctx = WorkshopContext.current();
        var b = req.body(ReasonRequest.class);
        return ServerResponse.ok().body(reopen.execute(new ReopenCashCountUseCase.Command(ctx, day(req), b.reason())));
    }

    ServerResponse alerts(ServerRequest req) {
        var filter = ListLeakAlertsUseCase.Filter.valueOf(req.param("status").orElse("PENDING"));
        return ServerResponse.ok().body(list.execute(WorkshopContext.current(), filter));
    }

    ServerResponse resolve(ServerRequest req) throws Exception {
        var ctx = WorkshopContext.current();
        var b = req.body(CommentRequest.class);
        resolve.execute(new ResolveLeakAlertUseCase.Command(ctx, UUID.fromString(req.pathVariable("id")), b.comment()));
        return ServerResponse.noContent().build();
    }

    private static LocalDate day(ServerRequest req) {
        try {
            return LocalDate.parse(req.pathVariable("day")); // ISO 8601 yyyy-MM-dd
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("INVALID_DAY", "La fecha debe tener el formato AAAA-MM-DD");
        }
    }
}
