package com.autoparts.inventory.security;

import com.autoparts.inventory.monitoring.AppEventMetrics;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// Not a @Component: it's wired into SecurityConfig's chain directly, so Boot's
// generic filter auto-registration doesn't also register it and double every log line.
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private final AppEventMetrics metrics;

    public RequestLoggingFilter(AppEventMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - start;
            int status = response.getStatus();
            metrics.recordHttpStatus(status);
            log.info("{} {} -> {} ({}ms)", request.getMethod(), request.getRequestURI(), status, durationMs);
        }
    }
}
