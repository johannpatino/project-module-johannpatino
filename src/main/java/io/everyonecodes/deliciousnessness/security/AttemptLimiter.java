package io.everyonecodes.deliciousnessness.security;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Counts attempts per key inside a sliding window. Kept in memory: a restart clears it.
public class AttemptLimiter {

    // Keys can be made up by the caller (any username), so the map is kept from growing without end.
    private static final int MAX_KEYS = 10_000;

    private final int maxAttempts;
    private final long windowMillis;
    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    public AttemptLimiter(int maxAttempts, Duration window) {
        this.maxAttempts = maxAttempts;
        this.windowMillis = window.toMillis();
    }

    public void record(String key) {
        if (attempts.size() > MAX_KEYS) {
            purgeExpired();
        }
        Deque<Long> times = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            dropOld(times);
            times.addLast(System.currentTimeMillis());
        }
    }

    public boolean isLimited(String key) {
        Deque<Long> times = attempts.get(key);
        if (times == null) {
            return false;
        }
        synchronized (times) {
            dropOld(times);
            return times.size() >= maxAttempts;
        }
    }

    public void clearAll() {
        attempts.clear();
    }

    public void clear(String key) {
        attempts.remove(key);
    }

    private void dropOld(Deque<Long> times) {
        long cutoff = System.currentTimeMillis() - windowMillis;
        while (!times.isEmpty() && times.peekFirst() < cutoff) {
            times.removeFirst();
        }
    }

    private void purgeExpired() {
        attempts.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                dropOld(entry.getValue());
                return entry.getValue().isEmpty();
            }
        });
    }
}
