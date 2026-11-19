package co.nitida.motorcycles.application;

import co.nitida.motorcycles.domain.ClaimCode;
import co.nitida.motorcycles.domain.ClaimCodeRepository;
import co.nitida.shared.ApiException;
import co.nitida.shared.RateLimiter;
import co.nitida.shared.crypto.SecureTokens;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Redeems claim and transfer codes. Unknown, used, expired and wrong-kind codes all answer the same 404, and each user
 * gets a few attempts per window, so codes cannot be guessed or probed (RN-80).
 */
@Component
class OwnerCodes {
    private final ClaimCodeRepository codes;
    private final Clock clock;
    // shortcut: in-memory, per instance (like login); move to a shared store when running more than one instance
    private final RateLimiter attempts;

    OwnerCodes(ClaimCodeRepository codes, Clock clock) {
        this.codes = codes;
        this.clock = clock;
        this.attempts = new RateLimiter(10, 15 * 60_000L, clock);
    }

    /** Marks the code used inside the caller's transaction; a rollback leaves it unused. */
    ClaimCode redeem(UUID userId, String code, String kind) {
        attempts.check(userId.toString());
        var found = codes.findByCodeHashAndKind(SecureTokens.hash(ClaimCode.normalize(code)), kind).orElseThrow(ApiException::notFound);
        found.use(clock.instant());
        return found;
    }
}
