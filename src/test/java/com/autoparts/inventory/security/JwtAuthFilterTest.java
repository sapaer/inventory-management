package com.autoparts.inventory.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {
    @Mock JwtService jwtService;
    @Mock TokenRevocationService revocations;

    private static final UUID USER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private JwtAuthFilter filter() {
        return new JwtAuthFilter(jwtService, revocations, true);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void noBearerHeaderPassesThroughUnauthenticated() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(new MockHttpServletRequest("GET", "/api/v1/inventory"), new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(revocations, never()).isRevoked(any());
    }

    @Test
    void validNonRevokedTokenAuthenticatesAndProceeds() throws Exception {
        when(jwtService.parseUserId("good-token")).thenReturn(USER);
        when(revocations.isRevoked(USER)).thenReturn(false);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/inventory");
        req.addHeader("Authorization", "Bearer good-token");
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(req, new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest());
        assertEquals(USER, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    void revokedTokenIs401AndStopsTheChain() throws Exception {
        when(jwtService.parseUserId("good-token")).thenReturn(USER);
        when(revocations.isRevoked(USER)).thenReturn(true);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/inventory");
        req.addHeader("Authorization", "Bearer good-token");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(req, res, chain);

        assertEquals(401, res.getStatus());
        assertNull(chain.getRequest(), "request must not reach the app");
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void revocationCheckCanBeDisabled() throws Exception {
        when(jwtService.parseUserId("good-token")).thenReturn(USER);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/inventory");
        req.addHeader("Authorization", "Bearer good-token");
        MockFilterChain chain = new MockFilterChain();

        new JwtAuthFilter(jwtService, revocations, false).doFilter(req, new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest());
        verify(revocations, never()).isRevoked(any());
    }

    @Test
    void unparseableTokenIs401() throws Exception {
        when(jwtService.parseUserId("bad")).thenThrow(new RuntimeException("bad sig"));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/inventory");
        req.addHeader("Authorization", "Bearer bad");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(req, res, chain);

        assertEquals(401, res.getStatus());
        assertNull(chain.getRequest());
        verify(revocations, never()).isRevoked(any());
    }
}
