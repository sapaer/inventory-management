package com.autoparts.inventory.security;

import com.autoparts.inventory.api.ApiEnvelope;
import com.autoparts.inventory.api.AppException;
import com.autoparts.inventory.config.RateLimitProperties;
import com.autoparts.inventory.store.RateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Per-client-IP fixed-window rate limiting. A looser cap covers the whole API; a tighter
 * one covers {@code /api/v1/auth/**} where OTP/password abuse concentrates. Wired into
 * {@link SecurityConfig}'s chain directly (not a {@code @Component}) so Boot does not also
 * auto-register it.
 */
public class RateLimitFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final RateLimiter limiter;
    private final RateLimitProperties props;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimiter limiter, RateLimitProperties props, ObjectMapper objectMapper) {
        this.limiter = limiter;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!props.isEnabled() || "OPTIONS".equalsIgnoreCase(request.getMethod()) || isExempt(path)) {
            chain.doFilter(request, response);
            return;
        }

        String ip = clientIp(request);
        try {
            limiter.enforce("rl:ip:" + ip, props.getIpRequestsPerMinute(), WINDOW,
                    "RATE_LIMITED", "Too many requests. Please slow down and try again shortly.");
            if (path.startsWith("/api/v1/auth/")) {
                limiter.enforce("rl:authip:" + ip, props.getAuthIpRequestsPerMinute(), WINDOW,
                        "RATE_LIMITED", "Too many authentication attempts. Try again in a minute.");
            }
        } catch (AppException ex) {
            log.warn("rate limit hit ip={} path={} code={}", ip, path, ex.getCode());
            writeTooMany(response, ex);
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isExempt(String path) {
        return path.equals("/health") || path.equals("/actuator/health") || path.startsWith("/actuator/");
    }

    /** First hop of {@code X-Forwarded-For} (set by Render's proxy), falling back to the socket address. */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            String first = (comma == -1 ? forwarded : forwarded.substring(0, comma)).trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "unknown" : remote;
    }

    private void writeTooMany(HttpServletResponse response, AppException ex) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiEnvelope.error(ex.getCode(), ex.getMessage()));
    }
}
