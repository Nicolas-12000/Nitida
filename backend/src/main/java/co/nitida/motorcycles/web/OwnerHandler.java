package co.nitida.motorcycles.web;

import co.nitida.identity.AuthenticatedUser;
import co.nitida.motorcycles.application.AcceptOwnershipTransferUseCase;
import co.nitida.motorcycles.application.ClaimMotorcycleUseCase;
import co.nitida.motorcycles.application.ListMyMotorcyclesUseCase;
import co.nitida.motorcycles.application.TransferOwnershipUseCase;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

@Component
class OwnerHandler {
    record ClaimRequest(String code) {}

    private final ClaimMotorcycleUseCase claim;
    private final ListMyMotorcyclesUseCase mine;
    private final TransferOwnershipUseCase transfer;
    private final AcceptOwnershipTransferUseCase accept;

    OwnerHandler(ClaimMotorcycleUseCase claim, ListMyMotorcyclesUseCase mine, TransferOwnershipUseCase transfer,
                 AcceptOwnershipTransferUseCase accept) {
        this.claim = claim;
        this.mine = mine;
        this.transfer = transfer;
        this.accept = accept;
    }

    ServerResponse claim(ServerRequest req) throws Exception {
        var user = AuthenticatedUser.current();
        var b = req.body(ClaimRequest.class);
        return ServerResponse.ok().body(claim.execute(new ClaimMotorcycleUseCase.Command(user.userId(), b.code())));
    }

    ServerResponse mine(ServerRequest req) {
        return ServerResponse.ok().body(mine.execute(AuthenticatedUser.current().userId()));
    }

    ServerResponse startTransfer(ServerRequest req) {
        var user = AuthenticatedUser.current();
        var result = transfer.execute(new TransferOwnershipUseCase.Command(user.userId(), UUID.fromString(req.pathVariable("id"))));
        return ServerResponse.status(HttpStatus.CREATED).body(result);
    }

    ServerResponse acceptTransfer(ServerRequest req) {
        var user = AuthenticatedUser.current();
        return ServerResponse.ok().body(accept.execute(new AcceptOwnershipTransferUseCase.Command(user.userId(), req.pathVariable("code"))));
    }
}
