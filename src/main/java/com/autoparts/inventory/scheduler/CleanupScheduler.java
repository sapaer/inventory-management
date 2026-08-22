package com.autoparts.inventory.scheduler;

import com.autoparts.inventory.repository.NotificationRepository;
import com.autoparts.inventory.store.AppKvStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Component
public class CleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(CleanupScheduler.class);
    private static final Duration NOTIFICATION_RETENTION = Duration.ofDays(1);

    private final NotificationRepository notifications;
    private final AppKvStore cache;

    public CleanupScheduler(NotificationRepository notifications, AppKvStore cache) {
        this.notifications = notifications;
        this.cache = cache;
    }

    @Transactional
    @Scheduled(fixedRate = 3_600_000)
    public void purgeOldNotifications() {
        long deleted = notifications.deleteByCreatedAtBefore(Instant.now().minus(NOTIFICATION_RETENTION));
        if (deleted > 0) {
            log.info("notification cleanup removed={}", deleted);
        }
    }

    @Scheduled(fixedRate = 3_600_000)
    public void purgeExpiredKvRows() {
        int purged = cache.purgeAllExpired();
        if (purged > 0) {
            log.info("app_kv_store cleanup removed={}", purged);
        }
    }
}
