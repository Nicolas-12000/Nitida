package co.nitida.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** Keys come from the client (an email, an IP): the map must not grow forever under a flood of distinct keys. */
class RateLimiterEvictionTest {
    static class MutableClock extends Clock {
        long now = 1_000_000;
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(now); }
        @Override public long millis() { return now; }
    }

    @Test
    void expiredKeysAreForgottenAndLiveKeysAreKept() {
        var clock = new MutableClock();
        var limiter = new RateLimiter(5, 1000, clock);
        for (int i = 0; i < 5000; i++) limiter.check("flood-" + i);
        assertThat(limiter.trackedKeys()).isGreaterThan(4000);

        clock.now += 2000; // every earlier key is outside the window now
        limiter.check("live");
        for (int i = 0; i < 2000; i++) limiter.check("again-" + i); // enough calls to run a cleanup pass
        clock.now += 500;
        for (int i = 0; i < 2000; i++) limiter.check("again-" + i);

        assertThat(limiter.trackedKeys()).as("old flood keys were dropped").isLessThan(2500);
    }

    @Test
    void cleanupNeverResetsAKeyThatIsStillInsideItsWindow() {
        var clock = new MutableClock();
        var limiter = new RateLimiter(3, 10_000, clock);
        for (int i = 0; i < 3; i++) limiter.check("victim");
        for (int i = 0; i < 5000; i++) limiter.check("noise-" + i); // forces cleanup passes while "victim" is live
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> limiter.check("victim")).isInstanceOf(ApiException.class);
    }
}
