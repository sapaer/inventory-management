package com.autoparts.inventory.monitoring;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunables for {@link MonitoringScheduler}. All overridable via {@code MONITORING_*} env vars
 * (see application.yml). Thresholds are "events within {@link #windowMinutes} minutes".
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.monitoring")
public class MonitoringProperties {
    /** Master switch for spike evaluation + alert emails. */
    private boolean enabled = true;

    private int windowMinutes = 5;

    private int http5xxThreshold = 10;
    private int http4xxThreshold = 50;
    private int otpFailureThreshold = 1;

    /** Minimum gap between two alert emails for the same signal. */
    private int alertCooldownMinutes = 30;

    /** Comma-separated recipients. Null/blank disables email (alerts still log at WARN). */
    private String alertTo;

    /** From address; falls back to spring.mail.username when blank. */
    private String alertFrom;
}
