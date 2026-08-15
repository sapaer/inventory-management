package com.autoparts.inventory.service;

import com.autoparts.inventory.api.AppException;
import com.autoparts.inventory.client.FcmClient;
import com.autoparts.inventory.client.WhatsAppClient;
import com.autoparts.inventory.enums.NotificationChannel;
import com.autoparts.inventory.enums.NotificationType;
import com.autoparts.inventory.entity.InventoryItem;
import com.autoparts.inventory.entity.Notification;
import com.autoparts.inventory.entity.User;
import com.autoparts.inventory.repository.NotificationRepository;
import com.autoparts.inventory.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final NotificationRepository notifications;
    private final UserRepository users;
    private final WhatsAppClient whatsapp;
    private final FcmClient fcm;

    public NotificationService(
            NotificationRepository notifications,
            UserRepository users,
            WhatsAppClient whatsapp,
            FcmClient fcm
    ) {
        this.notifications = notifications;
        this.users = users;
        this.whatsapp = whatsapp;
        this.fcm = fcm;
    }

    public void triggerLowStockCheck(InventoryItem item) {
        sendLowStockAlert(item);
    }

    public void sendLowStockAlert(InventoryItem item) {
        User user = users.findById(item.getUserId()).orElse(null);
        if (user == null) {
            return;
        }
        try {
            whatsapp.sendLowStockAlert(user.getPhone(), item.getPartName(), item.getQuantity());
        } catch (Exception ex) {
            log.error("low stock whatsapp failed itemId={}", item.getId(), ex);
        }
        fcm.sendLowStockPush(user.getId().toString(), item.getPartName(), item.getQuantity());
        Notification n = new Notification();
        n.setUserId(user.getId());
        n.setType(NotificationType.LOW_STOCK);
        n.setTitle("Low stock alert");
        n.setBody(item.getPartName() + " — only " + item.getQuantity() + " units left");
        n.setChannel(NotificationChannel.WHATSAPP);
        n.setData(Map.of("item_id", item.getId().toString(), "qty_remaining", item.getQuantity()));
        n.setSentAt(Instant.now());
        notifications.save(n);
    }

    public Map<String, Object> list(UUID userId, int page, int limit) {
        if (page < 1) page = 1;
        if (limit <= 0) limit = 20;
        Page<Notification> rows = notifications.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page - 1, limit));
        List<Map<String, Object>> content = rows.getContent().stream().map(NotificationService::toDto).toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("content", content);
        out.put("page", page);
        out.put("limit", limit);
        out.put("total", rows.getTotalElements());
        return out;
    }

    @Transactional
    public Map<String, Object> markRead(UUID userId, UUID id) {
        Notification n = notifications.findById(id)
                .filter(row -> row.getUserId().equals(userId))
                .orElseThrow(() -> AppException.notFound("Notification not found"));
        n.setRead(true);
        return toDto(notifications.save(n));
    }

    private static Map<String, Object> toDto(Notification n) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", n.getId());
        dto.put("type", n.getType());
        dto.put("title", n.getTitle());
        dto.put("body", n.getBody());
        dto.put("data", n.getData());
        dto.put("channel", n.getChannel());
        dto.put("isRead", n.isRead());
        dto.put("sentAt", n.getSentAt());
        dto.put("createdAt", n.getCreatedAt());
        return dto;
    }
}
