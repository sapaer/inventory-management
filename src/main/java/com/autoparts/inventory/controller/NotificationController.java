package com.autoparts.inventory.controller;

import com.autoparts.inventory.api.ApiEnvelope;
import com.autoparts.inventory.dto.NotificationResponse;
import com.autoparts.inventory.dto.PageResponse;
import com.autoparts.inventory.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<ApiEnvelope<PageResponse<NotificationResponse>>> list(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(ApiEnvelope.ok(notificationService.list(userId, page, limit)));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiEnvelope<NotificationResponse>> markRead(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(ApiEnvelope.ok(notificationService.markRead(userId, id)));
    }
}
