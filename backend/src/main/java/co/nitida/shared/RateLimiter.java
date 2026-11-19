package co.nitida.shared;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal in-memory sliding-window limiter (login, public links, serial probing).
 * shortcut: per-instance memory, move to Redis/Bucket4j when running more than one node.
 */
public class RateLimiter {
    private static final int CLEANUP_EVERY = 1024;

    private final int maxEvents;
    private final long windowMillis;
    private final Clock clock;
    private final ConcurrentHashMap<String, ArrayDeque<Long>> hits = new ConcurrentHashMap<>();
    private final AtomicInteger calls = new AtomicInteger();

    public RateLimiter(int maxEvents, long windowMillis, Clock clock) {
        this.maxEvents = maxEvents;
        this.windowMillis = windowMillis;
        this.clock = clock;
    }

    /** Records one attempt; throws 429 if the key exceeded the window budget. */
    public void check(String key) {
        long now = clock.millis();
        if (calls.incrementAndGet() % CLEANUP_EVERY == 0) evictExpired(now);
        var q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() >= windowMillis) q.pollFirst();
            if (q.size() >= maxEvents) throw ApiException.tooMany();
            q.addLast(now);
        }
    }

    public void reset(String key) { hits.remove(key); }

    /** Keys come from the client (an email, an IP): without this a flood of distinct keys grows the map forever. */
    private void evictExpired(long now) {
        // A hit racing with the removal of its own key can be lost: one extra attempt, never a lockout.
        hits.entrySet().removeIf(e -> {
            var q = e.getValue();
            synchronized (q) {
                return q.isEmpty() || now - q.peekLast() >= windowMillis;
            }
        });
    }

    int trackedKeys() { return hits.size(); }
}
