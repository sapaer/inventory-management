package com.autoparts.inventory.monitoring;

import com.autoparts.inventory.monitoring.AppEventMetrics.Signal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MonitoringSchedulerTest {
    @Mock AlertEmailSender email;

    private final AppEventMetrics metrics = new AppEventMetrics();

    private static MonitoringProperties props() {
        MonitoringProperties p = new MonitoringProperties();
        p.setWindowMinutes(5);
        p.setHttp5xxThreshold(3);
        p.setHttp4xxThreshold(50);
        p.setOtpFailureThreshold(1);
        p.setAlertCooldownMinutes(30);
        return p;
    }

    private MonitoringScheduler scheduler(MonitoringProperties p) {
        return new MonitoringScheduler(metrics, p, email);
    }

    @Test
    void noAlertBelowThreshold() {
        metrics.recordHttpStatus(500);
        metrics.recordHttpStatus(500);

        scheduler(props()).evaluate();

        verify(email, never()).send(any(), any());
    }

    @Test
    void alertsOnceThresholdReachedAndNamesTheSignal() {
        metrics.recordHttpStatus(500);
        metrics.recordHttpStatus(503);
        metrics.recordHttpStatus(500);

        scheduler(props()).evaluate();

        verify(email).send(contains("HTTP 5xx errors spike: 3"), contains("threshold: 3"));
    }

    @Test
    void otpFailureAlertsImmediately() {
        metrics.recordOtpDeliveryFailure();

        scheduler(props()).evaluate();

        verify(email).send(contains("OTP delivery failures spike: 1"), any());
    }

    @Test
    void cooldownSuppressesRepeatAlerts() {
        metrics.recordOtpDeliveryFailure();
        MonitoringScheduler s = scheduler(props());

        s.evaluate();
        s.evaluate();
        s.evaluate();

        verify(email, times(1)).send(any(), any());
    }

    @Test
    void disabledSkipsEverything() {
        MonitoringProperties p = props();
        p.setEnabled(false);
        metrics.recordOtpDeliveryFailure();

        scheduler(p).evaluate();

        verify(email, never()).send(any(), any());
    }

    @Test
    void zeroThresholdDisablesThatSignal() {
        MonitoringProperties p = props();
        p.setOtpFailureThreshold(0);
        metrics.recordOtpDeliveryFailure();
        metrics.recordOtpDeliveryFailure();

        scheduler(p).evaluate();

        verify(email, never()).send(contains("OTP"), any());
    }
}
