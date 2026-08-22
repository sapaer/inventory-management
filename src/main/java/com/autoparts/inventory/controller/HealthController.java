package com.autoparts.inventory.controller;

import com.autoparts.inventory.api.ApiEnvelope;
import com.autoparts.inventory.api.ErrorInfo;
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

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping({"/actuator/health", "/health"})
    public ResponseEntity<ApiEnvelope<Map<String, String>>> health() {
        String pg = pingPostgres();
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("status", "UP".equals(pg) ? "UP" : "DOWN");
        payload.put("postgres", pg);
        if (!"UP".equals(payload.get("status"))) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ApiEnvelope<>(false, payload, new ErrorInfo("UNHEALTHY", "Postgres is down", null), null));
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
