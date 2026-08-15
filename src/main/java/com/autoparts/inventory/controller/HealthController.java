package com.autoparts.inventory.controller;

import com.autoparts.inventory.api.ApiEnvelope;
import com.autoparts.inventory.store.RedisCache;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {
    private final DataSource dataSource;
    private final RedisCache redis;

    public HealthController(DataSource dataSource, RedisCache redis) {
        this.dataSource = dataSource;
        this.redis = redis;
    }

    @GetMapping({"/actuator/health", "/health"})
    public ResponseEntity<ApiEnvelope<Map<String, String>>> health() {
        String pg = pingPostgres();
        String rd = redis.ping() ? "UP" : "DOWN";
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("status", "UP".equals(pg) && "UP".equals(rd) ? "UP" : "DOWN");
        payload.put("postgres", pg);
        payload.put("redis", rd);
        if (!"UP".equals(payload.get("status"))) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ApiEnvelope<>(false, payload, new ApiEnvelope.ErrorInfo("UNHEALTHY", "One or more dependencies are down", null), null));
        }
        return ResponseEntity.ok(ApiEnvelope.ok(payload));
    }

    private String pingPostgres() {
        try (Connection c = dataSource.getConnection()) {
            return c.isValid(2) ? "UP" : "DOWN";
        } catch (Exception e) {
            return "DOWN";
        }
    }
}
