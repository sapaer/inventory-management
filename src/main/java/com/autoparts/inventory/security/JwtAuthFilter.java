package com.autoparts.inventory.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final TokenRevocationService revocations;
    /** Off-switch for the per-request revocation lookup (e.g. to shed DB load). */
    private final boolean revocationCheckEnabled;

    public JwtAuthFilter(
            JwtService jwtService,
            TokenRevocationService revocations,
            @Value("${app.security.revocation-check-enabled:true}") boolean revocationCheckEnabled
    ) {
        this.jwtService = jwtService;
        this.revocations = revocations;
        this.revocationCheckEnabled = revocationCheckEnabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }
        UUID userId;
        try {
            userId = jwtService.parseUserId(header.substring(7));
        } catch (Exception ex) {
            unauthorized(response, "Missing or invalid token");
            return;
        }
        if (revocationCheckEnabled && revocations.isRevoked(userId)) {
            unauthorized(response, "Session ended. Please sign in again.");
            return;
        }
        var auth = new UsernamePasswordAuthenticationToken(userId, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
        chain.doFilter(request, response);
    }

    private static void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(401);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"success\":false,\"data\":null,\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"" + message + "\"}}");
    }
}
