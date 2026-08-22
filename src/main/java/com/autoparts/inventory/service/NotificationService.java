package com.autoparts.inventory.service;

import com.autoparts.inventory.api.AppException;
import com.autoparts.inventory.client.FcmClient;
import com.autoparts.inventory.client.SmsClient;
import com.autoparts.inventory.client.WhatsAppClient;
import com.autoparts.inventory.enums.NotificationChannel;
import com.autoparts.inventory.enums.NotificationType;
import com.autoparts.inventory.dto.NotificationResponse;
import com.autoparts.inventory.dto.PageResponse;
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
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final NotificationRepository notifications;
    private final UserRepository users;
    private final WhatsAppClient whatsapp;
    private final SmsClient sms;
    private final FcmClient fcm;

    public NotificationService(
            NotificationRepository notifications,
            UserRepository users,
            WhatsAppClient whatsapp,
            SmsClient sms,
            FcmClient fcm
    ) {
        this.notifications = notifications;
        this.users = users;
        this.whatsapp = whatsapp;
        this.sms = sms;
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
        boolean whatsappSent = false;
        boolean smsSent = false;
        try {
            if (whatsapp.configured()) {
                whatsapp.sendLowStockAlert(user.getPhone(), item.getPartName(), item.getQuantity());
                whatsappSent = true;
            }
        } catch (Exception ex) {
            log.error("low stock whatsapp failed itemId={}", item.getId(), ex);
        }
        if (sms.configured()) {
            try {
                sms.sendLowStockAlert(user.getPhone(), item.getPartName(), item.getQuantity());
                smsSent = true;
            } catch (Exception ex) {
                log.error("low stock sms failed itemId={}", item.getId(), ex);
            }
        }
        fcm.sendLowStockPush(user.getId().toString(), item.getPartName(), item.getQuantity());
        Notification n = new Notification();
        n.setUserId(user.getId());
        n.setType(NotificationType.LOW_STOCK);
        n.setTitle("Low stock alert");
        n.setBody(item.getPartName() + " — only " + item.getQuantity() + " units left");
        n.setChannel(whatsappSent ? NotificationChannel.WHATSAPP
                : smsSent ? NotificationChannel.SMS
                : NotificationChannel.IN_APP);
        n.setData(Map.of("item_id", item.getId().toString(), "qty_remaining", item.getQuantity()));
        n.setSentAt(Instant.now());
        notifications.save(n);
    }

    public PageResponse<NotificationResponse> list(UUID userId, int page, int limit) {
        if (page < 1) page = 1;
        if (limit <= 0) limit = 20;
        Page<Notification> rows = notifications.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page - 1, limit));
        return new PageResponse<>(rows.getContent().stream().map(NotificationService::toResponse).toList(),
                page, limit, rows.getTotalElements());
    }

    @Transactional
    public NotificationResponse markRead(UUID userId, UUID id) {
        Notification n = notifications.findById(id)
                .filter(row -> row.getUserId().equals(userId))
                .orElseThrow(() -> AppException.notFound("Notification not found"));
        n.setRead(true);
        return toResponse(notifications.save(n));
    }

    private static NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getType(),
                n.getTitle(),
                n.getBody(),
                n.getData(),
                n.getChannel(),
                n.isRead(),
                n.getSentAt(),
                n.getCreatedAt()
        );
    }
}
