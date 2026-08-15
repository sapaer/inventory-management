package com.autoparts.inventory.scheduler;

import com.autoparts.inventory.entity.InventoryItem;
import com.autoparts.inventory.repository.InventoryItemRepository;
import com.autoparts.inventory.service.NotificationService;
import com.autoparts.inventory.store.RedisCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class LowStockScheduler {
    private static final Logger log = LoggerFactory.getLogger(LowStockScheduler.class);
    private final InventoryItemRepository items;
    private final NotificationService notifications;
    private final RedisCache cache;

    public LowStockScheduler(InventoryItemRepository items, NotificationService notifications, RedisCache cache) {
        this.items = items;
        this.notifications = notifications;
        this.cache = cache;
    }

    @Scheduled(fixedRate = 300_000)
    public void checkLowStockItems() {
        log.info("low stock cron started");
        int checked = 0;
        int notified = 0;
        List<InventoryItem> low = items.findAllLowStockActive();
        for (InventoryItem item : low) {
            checked++;
            String key = "low_stock_notified:" + item.getId();
            if (cache.exists(key)) {
                continue;
            }
            try {
                notifications.sendLowStockAlert(item);
                cache.set(key, "1", Duration.ofDays(1));
                notified++;
            } catch (Exception ex) {
                log.error("low stock alert failed itemId={}", item.getId(), ex);
            }
        }
        log.info("low stock cron done checked={} notified={}", checked, notified);
    }
}
