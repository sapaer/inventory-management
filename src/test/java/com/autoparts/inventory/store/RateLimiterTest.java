package com.autoparts.inventory.store;

import com.autoparts.inventory.api.AppException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimiterTest {
    @Mock AppKvStore store;

    @Test
    void firstHitSetsTheWindowTtl() {
        when(store.incr("k")).thenReturn(1L);

        long n = new RateLimiter(store).hit("k", Duration.ofMinutes(1));

        assertEquals(1L, n);
        verify(store).expire("k", Duration.ofMinutes(1));
    }

    @Test
    void laterHitsDoNotResetTheWindow() {
        when(store.incr("k")).thenReturn(3L);

        new RateLimiter(store).hit("k", Duration.ofMinutes(1));

        verify(store, never()).expire(org.mockito.ArgumentMatchers.eq("k"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void enforceThrowsOnceCountExceedsLimit() {
        when(store.incr("k")).thenReturn(6L);

        AppException ex = assertThrows(AppException.class, () ->
                new RateLimiter(store).enforce("k", 5, Duration.ofMinutes(1), "RATE_LIMITED", "nope"));
        assertEquals("RATE_LIMITED", ex.getCode());
    }

    @Test
    void enforceAllowsUpToTheLimit() {
        when(store.incr("k")).thenReturn(5L);

        new RateLimiter(store).enforce("k", 5, Duration.ofMinutes(1), "RATE_LIMITED", "nope");
    }

    @Test
    void isExceededReadsWithoutCounting() {
        when(store.get("k")).thenReturn("5");

        assertTrue(new RateLimiter(store).isExceeded("k", 5));
        assertFalse(new RateLimiter(store).isExceeded("missing", 5));
        verify(store, never()).incr(org.mockito.ArgumentMatchers.any());
    }
}
