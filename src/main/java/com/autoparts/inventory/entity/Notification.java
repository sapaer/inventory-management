package com.autoparts.inventory.entity;

import com.autoparts.inventory.enums.NotificationChannel;
import com.autoparts.inventory.enums.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    private String title;
    private String body;

    @Convert(converter = JsonMapConverter.class)
    @Column(columnDefinition = "json")
    private Map<String, Object> data = new HashMap<>();

    @Enumerated(EnumType.STRING)
    private NotificationChannel channel;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }
}
