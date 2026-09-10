package com.autoparts.inventory.monitoring;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlidingWindowCounterTest {
    private final AtomicLong clock = new AtomicLong(1_000_000_000L);

    private SlidingWindowCounter counter() {
        return new SlidingWindowCounter(clock::get);
    }

    private void advanceSeconds(long s) {
        clock.addAndGet(s * 1000L);
    }

    @Test
    void countsEventsInsideTheWindow() {
        SlidingWindowCounter c = counter();
        c.increment();
        c.increment();
        advanceSeconds(120);
        c.increment();

        assertEquals(3, c.countWithin(Duration.ofMinutes(5)));
    }

    @Test
    void dropsEventsOlderThanTheWindow() {
        SlidingWindowCounter c = counter();
        c.increment();
        advanceSeconds(600); // 10 min later
        c.increment();

        assertEquals(1, c.countWithin(Duration.ofMinutes(5)));
    }

    @Test
    void pruneReleasesOldBuckets() {
        SlidingWindowCounter c = counter();
        c.increment();
        advanceSeconds(3600);
        c.pruneOlderThan(Duration.ofMinutes(20));
        // window still counts nothing; underlying bucket is gone
        assertEquals(0, c.countWithin(Duration.ofHours(2)));
    }
}
