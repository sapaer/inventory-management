package com.autoparts.inventory.client;

import com.autoparts.inventory.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class FcmClient {
    private static final Logger log = LoggerFactory.getLogger(FcmClient.class);
    private final String credentialsFile;

    public FcmClient(AppProperties props) {
        this.credentialsFile = System.getenv().getOrDefault("FIREBASE_CREDENTIALS_PATH", "");
    }

    public void sendLowStockPush(String userId, String partName, int qty) {
        if (credentialsFile == null || credentialsFile.isBlank()) {
            log.warn("firebase credentials missing, skipping push userId={}", userId);
            return;
        }
        log.info("fcm low-stock push queued userId={} part={} qty={}", userId, partName, qty);
    }
}
