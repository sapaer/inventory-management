package com.autoparts.inventory.monitoring;

import com.autoparts.inventory.monitoring.AppEventMetrics.Signal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every minute, checks each monitored signal against its threshold over the rolling
 * window. On a breach it logs at WARN (so it reaches Better Stack) and emails the
 * on-call address, then holds a per-signal cooldown so one incident is one email.
 */
@Component
public class MonitoringScheduler {
    private static final Logger log = LoggerFactory.getLogger(MonitoringScheduler.class);

    private final AppEventMetrics metrics;
    private final MonitoringProperties props;
    private final AlertEmailSender email;
    private final Map<Signal, Instant> lastAlertAt = new ConcurrentHashMap<>();

    public MonitoringScheduler(AppEventMetrics metrics, MonitoringProperties props, AlertEmailSender email) {
        this.metrics = metrics;
        this.props = props;
        this.email = email;
    }

    @Scheduled(fixedRate = 60_000)
    public void evaluate() {
        if (!props.isEnabled()) {
            return;
        }
        Duration window = Duration.ofMinutes(props.getWindowMinutes());
        metrics.prune(window.multipliedBy(4));

        check(Signal.HTTP_5XX, "HTTP 5xx errors", props.getHttp5xxThreshold(), window);
        check(Signal.HTTP_4XX, "HTTP 4xx responses", props.getHttp4xxThreshold(), window);
        check(Signal.OTP_DELIVERY_FAILURE, "OTP delivery failures", props.getOtpFailureThreshold(), window);
    }

    private void check(Signal signal, String label, int threshold, Duration window) {
        if (threshold <= 0) {
            return;
        }
        long count = metrics.countWithin(signal, window);
        if (count < threshold) {
            return;
        }
        Instant now = Instant.now();
        Instant last = lastAlertAt.get(signal);
        boolean inCooldown = last != null
                && last.plus(Duration.ofMinutes(props.getAlertCooldownMinutes())).isAfter(now);
        if (inCooldown) {
            return;
        }
        lastAlertAt.put(signal, now);

        String subject = "[inventory-api] %s spike: %d in %dm".formatted(label, count, window.toMinutes());
        String body = """
                %s

                %d %s in the last %d minute(s) (threshold: %d).
                Time: %s

                Investigate in Better Stack (query: level:ERROR or the request log for this window).
                Next alert for this signal is suppressed for %d minute(s).
                """.formatted(subject, count, label, window.toMinutes(), threshold, now,
                props.getAlertCooldownMinutes());

        log.warn("MONITORING ALERT: {} (count={} threshold={} window={}m)",
                label, count, threshold, window.toMinutes());
        email.send(subject, body);
    }
}
