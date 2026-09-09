package com.autoparts.inventory.security;

import com.autoparts.inventory.api.AppException;
import com.autoparts.inventory.config.RateLimitProperties;
import com.autoparts.inventory.store.RateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {
    @Mock RateLimiter limiter;
    private final RateLimitProperties props = new RateLimitProperties();
    private final ObjectMapper mapper = new ObjectMapper();

    private RateLimitFilter filter() {
        return new RateLimitFilter(limiter, props, mapper);
    }

    private static MockHttpServletRequest req(String method, String uri, String ip) {
        MockHttpServletRequest r = new MockHttpServletRequest(method, uri);
        r.setRemoteAddr(ip);
        return r;
    }

    @Test
    void healthCheckIsExempt() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(req("GET", "/health", "1.2.3.4"), res, chain);

        assertNotNull(chain.getRequest());
        verify(limiter, never()).enforce(any(), anyInt(), any(), any(), any());
    }

    @Test
    void authPathChecksBothGlobalAndAuthBuckets() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(req("POST", "/api/v1/auth/otp/request", "9.9.9.9"), res, chain);

        assertNotNull(chain.getRequest());
        verify(limiter).enforce(eq("rl:ip:9.9.9.9"), eq(props.getIpRequestsPerMinute()),
                eq(Duration.ofMinutes(1)), eq("RATE_LIMITED"), any());
        verify(limiter).enforce(eq("rl:authip:9.9.9.9"), eq(props.getAuthIpRequestsPerMinute()),
                eq(Duration.ofMinutes(1)), eq("RATE_LIMITED"), any());
    }

    @Test
    void overLimitReturns429AndStopsTheChain() throws Exception {
        doThrow(AppException.tooManyRequests("RATE_LIMITED", "slow down"))
                .when(limiter).enforce(eq("rl:ip:5.5.5.5"), anyInt(), any(), any(), any());
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(req("GET", "/api/v1/inventory", "5.5.5.5"), res, chain);

        assertEquals(429, res.getStatus());
        assertEquals("application/json", res.getContentType());
        assertNull(chain.getRequest(), "chain must not proceed once rate limited");
    }

    @Test
    void forwardedForHeaderWinsOverSocketAddress() throws Exception {
        MockHttpServletRequest request = req("GET", "/api/v1/inventory", "10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter().doFilter(request, res, new MockFilterChain());

        verify(limiter).enforce(eq("rl:ip:203.0.113.7"), anyInt(), any(), any(), any());
    }

    @Test
    void disabledFilterIsBypassed() throws Exception {
        props.setEnabled(false);
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(req("POST", "/api/v1/auth/otp/verify", "1.1.1.1"), new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest());
        verify(limiter, never()).enforce(any(), anyInt(), any(), any(), any());
    }
}
