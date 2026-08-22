package com.autoparts.inventory.dto;

import com.autoparts.inventory.enums.NotificationChannel;
import com.autoparts.inventory.enums.NotificationType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class NotificationResponse {
    private final UUID id;
    private final NotificationType type;
    private final String title;
    private final String body;
    private final Map<String, Object> data;
    private final NotificationChannel channel;

    @JsonProperty("isRead")
    private final boolean read;

    private final Instant sentAt;
    private final Instant createdAt;
}
