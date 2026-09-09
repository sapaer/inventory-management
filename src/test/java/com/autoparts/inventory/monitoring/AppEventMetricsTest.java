package com.autoparts.inventory.monitoring;

import com.autoparts.inventory.monitoring.AppEventMetrics.Signal;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppEventMetricsTest {
    private final AppEventMetrics metrics = new AppEventMetrics();
    private final Duration window = Duration.ofMinutes(5);

    @Test
    void routesStatusCodesToTheRightBucket() {
        metrics.recordHttpStatus(200);
        metrics.recordHttpStatus(404);
        metrics.recordHttpStatus(429);
        metrics.recordHttpStatus(500);
        metrics.recordHttpStatus(503);

        assertEquals(2, metrics.countWithin(Signal.HTTP_4XX, window));
        assertEquals(2, metrics.countWithin(Signal.HTTP_5XX, window));
    }

    @Test
    void otpDeliveryFailureIsItsOwnSignal() {
        metrics.recordOtpDeliveryFailure();
        metrics.recordOtpDeliveryFailure();

        assertEquals(2, metrics.countWithin(Signal.OTP_DELIVERY_FAILURE, window));
        assertEquals(0, metrics.countWithin(Signal.HTTP_5XX, window));
    }
}
