package com.autoparts.inventory.security;

import com.autoparts.inventory.api.ApiEnvelope;
import com.autoparts.inventory.config.RateLimitProperties;
import com.autoparts.inventory.monitoring.AppEventMetrics;
import com.autoparts.inventory.store.RateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
public class SecurityConfig {
    private final JwtAuthFilter jwtAuthFilter;
    private final ObjectMapper objectMapper;
    private final CorsConfigurationSource corsConfigurationSource;
    private final RateLimiter rateLimiter;
    private final RateLimitProperties rateLimitProperties;
    private final AppEventMetrics appEventMetrics;

    public SecurityConfig(
            JwtAuthFilter jwtAuthFilter,
            ObjectMapper objectMapper,
            CorsConfigurationSource corsConfigurationSource,
            RateLimiter rateLimiter,
            RateLimitProperties rateLimitProperties,
            AppEventMetrics appEventMetrics
    ) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.objectMapper = objectMapper;
        this.corsConfigurationSource = corsConfigurationSource;
        this.rateLimiter = rateLimiter;
        this.rateLimitProperties = rateLimitProperties;
        this.appEventMetrics = appEventMetrics;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/health").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/otp/request", "/api/v1/auth/otp/verify",
                                "/api/v1/auth/token/refresh", "/api/v1/auth/accounts/select",
                                "/api/v1/auth/password/login",
                                "/api/v1/auth/password/forgot/request",
                                "/api/v1/auth/password/forgot/reset").permitAll()
                        // Twilio can't send a Bearer token; TwilioSignatureValidator authenticates these instead.
                        .requestMatchers(HttpMethod.POST, "/api/v1/voice/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint((req, res, e) -> writeUnauthorized(res)))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new RateLimitFilter(rateLimiter, rateLimitProperties, objectMapper), JwtAuthFilter.class)
                .addFilterBefore(new RequestLoggingFilter(appEventMetrics), RateLimitFilter.class);
        return http.build();
    }

    private void writeUnauthorized(HttpServletResponse res) throws java.io.IOException {
        res.setStatus(401);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(res.getWriter(), ApiEnvelope.error("UNAUTHORIZED", "Missing or invalid token"));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
