package co.nitida.history.application;

import co.nitida.history.application.HistoryViews.HistoryView;
import co.nitida.history.domain.PublicLinkRepository;
import co.nitida.intake.IntakeApi;
import co.nitida.shared.ApiException;
import co.nitida.shared.RateLimiter;
import co.nitida.shared.WorkshopContext;
import co.nitida.shared.crypto.SecureTokens;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-16 (public, by token) and the workshop-side view of an attended motorcycle. */
@Service
public class ViewHistoryUseCases {
    public record PublicCommand(String token, String ip) {}

    public record WorkshopCommand(WorkshopContext ctx, UUID motorcycleId) {}

    private final PublicLinkRepository links;
    private final IntakeApi intakes;
    private final HistoryViews views;
    private final Clock clock;
    private final RateLimiter publicLimiter;

    ViewHistoryUseCases(PublicLinkRepository links, IntakeApi intakes, HistoryViews views, Clock clock) {
        this.links = links;
        this.intakes = intakes;
        this.views = views;
        this.clock = clock;
        this.publicLimiter = new RateLimiter(60, 60_000, clock); // per IP and minute (RN-74)
    }

    /** Unknown, expired and revoked tokens are indistinguishable: the same generic 404 (RN-74). */
    @Transactional(readOnly = true)
    public HistoryView viewPublic(PublicCommand c) {
        publicLimiter.check(c.ip());
        var link = links.findByTokenHash(SecureTokens.hash(c.token() == null ? "" : c.token()))
                .filter(l -> l.isValidAt(clock.instant()))
                .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "LINK_NOT_AVAILABLE",
                        "Este enlace no está disponible"));
        return views.of(link.getMotorcycleId());
    }

    @Transactional(readOnly = true)
    public HistoryView viewAsWorkshop(WorkshopCommand c) {
        if (!intakes.hasReceived(c.ctx().workshopId(), c.motorcycleId())) throw ApiException.notFound();
        return views.of(c.motorcycleId());
    }
}
