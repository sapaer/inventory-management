package com.autoparts.inventory.store;

import com.autoparts.inventory.api.AppException;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Fixed-window counter on top of {@link AppKvStore}. The first hit in a window sets the
 * TTL; the row disappears when the window elapses, starting the next window fresh.
 */
@Component
public class RateLimiter {
    private final AppKvStore store;

    public RateLimiter(AppKvStore store) {
        this.store = store;
    }

    /** Count one hit against {@code key} and return the running total for the current window. */
    public long hit(String key, Duration window) {
        long count = store.incr(key);
        if (count == 1L) {
            store.expire(key, window);
        }
        return count;
    }

    /** Count one hit and throw {@code 429} when it pushes the window total past {@code limit}. */
    public void enforce(String key, int limit, Duration window, String code, String message) {
        if (hit(key, window) > limit) {
            throw AppException.tooManyRequests(code, message);
        }
    }

    /** Read-only check used as a pre-flight guard before doing expensive work (does not count). */
    public boolean isExceeded(String key, int limit) {
        String v = store.get(key);
        if (v == null || v.isBlank()) {
            return false;
        }
        try {
            return Long.parseLong(v) >= limit;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    public void reset(String... keys) {
        store.delete(keys);
    }
}
