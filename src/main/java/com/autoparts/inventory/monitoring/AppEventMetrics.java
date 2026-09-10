package com.autoparts.inventory.monitoring;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/**
 * In-memory rolling counters for the signals we alert on: HTTP 4xx / 5xx response
 * rates and OTP delivery failures. Counts reset on restart, which is fine for
 * short-window spike detection.
 */
@Component
public class AppEventMetrics {
    public enum Signal {
        HTTP_4XX,
        HTTP_5XX,
        OTP_DELIVERY_FAILURE
    }

    private final Map<Signal, SlidingWindowCounter> counters = new EnumMap<>(Signal.class);

    public AppEventMetrics() {
        for (Signal signal : Signal.values()) {
            counters.put(signal, new SlidingWindowCounter());
        }
    }

    /** Called per response by {@code RequestLoggingFilter}. */
    public void recordHttpStatus(int status) {
        if (status >= 500) {
            counters.get(Signal.HTTP_5XX).increment();
        } else if (status >= 400) {
            counters.get(Signal.HTTP_4XX).increment();
        }
    }

    public void recordOtpDeliveryFailure() {
        counters.get(Signal.OTP_DELIVERY_FAILURE).increment();
    }

    public long countWithin(Signal signal, Duration window) {
        return counters.get(signal).countWithin(window);
    }

    /** Drop buckets older than {@code keep} so memory stays bounded. */
    public void prune(Duration keep) {
        counters.values().forEach(counter -> counter.pruneOlderThan(keep));
    }
}
