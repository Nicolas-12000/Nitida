package co.nitida.shared;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal in-memory sliding-window limiter (login, public links, serial probing).
 * shortcut: per-instance memory, move to Redis/Bucket4j when running more than one node.
 */
public class RateLimiter {
    private final int maxEvents;
    private final long windowMillis;
    private final Clock clock;
    private final ConcurrentHashMap<String, ArrayDeque<Long>> hits = new ConcurrentHashMap<>();

    public RateLimiter(int maxEvents, long windowMillis, Clock clock) {
        this.maxEvents = maxEvents;
        this.windowMillis = windowMillis;
        this.clock = clock;
    }

    /** Records one attempt; throws 429 if the key exceeded the window budget. */
    public void check(String key) {
        long now = clock.millis();
        var q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() >= windowMillis) q.pollFirst();
            if (q.size() >= maxEvents) throw ApiException.tooMany();
            q.addLast(now);
        }
    }

    public void reset(String key) { hits.remove(key); }
}
