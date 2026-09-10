package com.autoparts.inventory.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunables for {@link com.autoparts.inventory.security.RateLimitFilter} (per-IP) and the
 * per-phone OTP limits in {@link com.autoparts.inventory.service.AuthService}.
 * All counters live in {@code app_kv_store} via {@link com.autoparts.inventory.store.AppKvStore}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {
    /** Master switch for the per-IP filter. Per-phone OTP limits always apply. */
    private boolean enabled = true;

    /** Max requests per client IP per minute across the whole API. */
    private int ipRequestsPerMinute = 120;

    /** Tighter per-minute cap per client IP for {@code /api/v1/auth/**}. */
    private int authIpRequestsPerMinute = 20;

    /** Max OTP sends per phone within {@link #otpRequestWindowSeconds}. */
    private int otpRequestPerPhone = 5;
    private int otpRequestWindowSeconds = 600;

    /** Max wrong OTP submissions per phone within {@link #otpVerifyWindowSeconds}. */
    private int otpVerifyPerPhone = 5;
    private int otpVerifyWindowSeconds = 600;
}
