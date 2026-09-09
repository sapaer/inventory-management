package com.autoparts.inventory.monitoring;

import java.time.Duration;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.LongSupplier;

/**
 * Thread-safe count of events bucketed by wall-clock second, so a caller can ask
 * "how many in the last N minutes". Memory is bounded by however far back
 * {@link #pruneOlderThan} is called with.
 */
final class SlidingWindowCounter {
    private final ConcurrentSkipListMap<Long, LongAdder> buckets = new ConcurrentSkipListMap<>();
    private final LongSupplier nowMillis;

    SlidingWindowCounter() {
        this(System::currentTimeMillis);
    }

    SlidingWindowCounter(LongSupplier nowMillis) {
        this.nowMillis = nowMillis;
    }

    void increment() {
        buckets.computeIfAbsent(nowSecond(), s -> new LongAdder()).increment();
    }

    long countWithin(Duration window) {
        long from = nowSecond() - Math.max(1L, window.toSeconds());
        long total = 0L;
        for (LongAdder adder : buckets.tailMap(from, false).values()) {
            total += adder.sum();
        }
        return total;
    }

    void pruneOlderThan(Duration keep) {
        long cutoff = nowSecond() - Math.max(1L, keep.toSeconds());
        buckets.headMap(cutoff, true).clear();
    }

    private long nowSecond() {
        return nowMillis.getAsLong() / 1000L;
    }
}
