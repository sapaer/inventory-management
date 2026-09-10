package com.autoparts.inventory.service;

import com.autoparts.inventory.api.AppException;
import com.autoparts.inventory.dto.AccountDeletionResponse;
import com.autoparts.inventory.entity.InventoryItem;
import com.autoparts.inventory.entity.User;
import com.autoparts.inventory.entity.UserLocation;
import com.autoparts.inventory.enums.AccountStatus;
import com.autoparts.inventory.repository.InventoryItemRepository;
import com.autoparts.inventory.repository.NotificationRepository;
import com.autoparts.inventory.repository.UserLocationRepository;
import com.autoparts.inventory.repository.UserRepository;
import com.autoparts.inventory.security.TokenRevocationService;
import com.autoparts.inventory.store.AppKvStore;
import com.autoparts.inventory.util.CsvWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Account deletion lifecycle + data export.
 *
 * <p>DELETE /account marks the row {@link AccountStatus#PENDING_DELETION}, stamps
 * {@code deletionRequestedAt}, and revokes the caller's tokens. {@link #purgeExpiredDeletions()}
 * (daily cron) hard-deletes rows whose grace period has elapsed. Recovery is by logging back
 * in within the 30 days — that path lives in {@link AuthService#reactivateAndVerify}.
 */
@Service
public class AccountService {
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    static final Duration DELETION_GRACE = Duration.ofDays(30);

    private final UserRepository users;
    private final InventoryItemRepository items;
    private final NotificationRepository notifications;
    private final UserLocationRepository locations;
    private final AppKvStore cache;
    private final TokenRevocationService revocations;

    public AccountService(
            UserRepository users,
            InventoryItemRepository items,
            NotificationRepository notifications,
            UserLocationRepository locations,
            AppKvStore cache,
            TokenRevocationService revocations
    ) {
        this.users = users;
        this.items = items;
        this.notifications = notifications;
        this.locations = locations;
        this.cache = cache;
        this.revocations = revocations;
    }

    /** DELETE /account — schedule this account for deletion. Idempotent. */
    @Transactional
    public AccountDeletionResponse requestDeletion(UUID userId) {
        User user = users.findById(userId).orElseThrow(() -> AppException.notFound("User not found"));
        if (user.getStatus() != AccountStatus.PENDING_DELETION) {
            user.setStatus(AccountStatus.PENDING_DELETION);
            user.setDeletionRequestedAt(Instant.now());
            users.save(user);
            cache.delete("session:" + userId);
            revocations.revoke(userId);
            log.warn("account deletion requested userId={} purgeAfter={}",
                    userId, user.getDeletionRequestedAt().plus(DELETION_GRACE));
        }
        return new AccountDeletionResponse(
                user.getStatus(),
                user.getDeletionRequestedAt(),
                user.getDeletionRequestedAt().plus(DELETION_GRACE));
    }

    /** Cron entry point: hard-delete every account whose grace period has elapsed. */
    @Transactional
    public int purgeExpiredDeletions() {
        Instant cutoff = Instant.now().minus(DELETION_GRACE);
        List<User> due = users.findByDeletionRequestedAtBefore(cutoff);
        for (User user : due) {
            hardDelete(user);
        }
        if (!due.isEmpty()) {
            log.warn("account purge removed={} accounts", due.size());
        }
        return due.size();
    }

    private void hardDelete(User user) {
        UUID userId = user.getId();
        int parts = items.deleteByUserId(userId);
        int notifs = notifications.deleteByUserId(userId);
        int locs = locations.deleteByUserId(userId);
        cache.delete("session:" + userId);
        users.delete(user);
        log.warn("account purged userId={} parts={} notifications={} locations={}", userId, parts, notifs, locs);
    }

    /** GET /account/export — profile + full inventory as one CSV document. */
    @Transactional(readOnly = true)
    public String exportCsv(UUID userId) {
        User user = users.findById(userId).orElseThrow(() -> AppException.notFound("User not found"));
        UserLocation loc = locations.findByUserId(userId).orElse(null);
        List<InventoryItem> inventory = items.findByUserIdOrderByCreatedAtAsc(userId);

        CsvWriter csv = new CsvWriter();
        csv.row("PROFILE");
        csv.row("field", "value");
        csv.row("account_id", user.getId());
        csv.row("phone", user.getPhone());
        csv.row("first_name", user.getFirstName());
        csv.row("last_name", user.getLastName());
        csv.row("shop_name", user.getShopName());
        csv.row("email", user.getEmail());
        csv.row("business_type", user.getBusinessType());
        csv.row("onboarding_status", user.getOnboardingStatus());
        csv.row("account_status", user.getStatus());
        csv.row("vehicle_categories", String.join(" ", user.getVehicleCategories().stream().map(Enum::name).toList()));
        csv.row("address", loc == null ? null : loc.getAddress());
        csv.row("area", loc == null ? null : loc.getArea());
        csv.row("city", loc == null ? null : loc.getCity());
        csv.row("state", loc == null ? null : loc.getState());
        csv.row("pincode", loc == null ? null : loc.getPincode());
        csv.row("created_at", user.getCreatedAt());

        csv.blankLine();
        csv.row("INVENTORY", String.valueOf(inventory.size()));
        csv.row("part_name", "local_name", "specification", "description", "vehicle_category",
                "brand", "model", "quantity", "min_quantity", "selling_price", "cost_price",
                "stock_status", "active", "created_at", "updated_at");
        for (InventoryItem i : inventory) {
            csv.row(i.getPartName(), i.getLocalName(), i.getSpecification(), i.getDescription(),
                    i.getVehicleCategory(), i.getBrand(), i.getModel(), i.getQuantity(), i.getMinQuantity(),
                    i.getSellingPrice(), i.getCostPrice(), i.stockStatus(), i.isActive(),
                    i.getCreatedAt(), i.getUpdatedAt());
        }
        return csv.toString();
    }
}
