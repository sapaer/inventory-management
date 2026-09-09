package com.autoparts.inventory.service;

import com.autoparts.inventory.dto.AccountDeletionResponse;
import com.autoparts.inventory.entity.InventoryItem;
import com.autoparts.inventory.entity.User;
import com.autoparts.inventory.enums.AccountStatus;
import com.autoparts.inventory.repository.InventoryItemRepository;
import com.autoparts.inventory.repository.NotificationRepository;
import com.autoparts.inventory.repository.UserLocationRepository;
import com.autoparts.inventory.repository.UserRepository;
import com.autoparts.inventory.security.TokenRevocationService;
import com.autoparts.inventory.store.AppKvStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {
    @Mock UserRepository users;
    @Mock InventoryItemRepository items;
    @Mock NotificationRepository notifications;
    @Mock UserLocationRepository locations;
    @Mock AppKvStore cache;
    @Mock TokenRevocationService revocations;

    private AccountService svc() {
        return new AccountService(users, items, notifications, locations, cache, revocations);
    }

    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static User user(AccountStatus status) {
        User u = new User();
        u.setId(USER_ID);
        u.setPhone("8619544044");
        u.setStatus(status);
        return u;
    }

    @Test
    void requestDeletionMarksPendingAndKillsSession() {
        User u = user(AccountStatus.ACTIVE);
        when(users.findById(USER_ID)).thenReturn(Optional.of(u));

        AccountDeletionResponse res = svc().requestDeletion(USER_ID);

        assertEquals(AccountStatus.PENDING_DELETION, u.getStatus());
        assertEquals(u.getDeletionRequestedAt().plus(30, ChronoUnit.DAYS), res.getPurgeAfter());
        verify(users).save(u);
        verify(cache).delete("session:" + USER_ID);
        verify(revocations).revoke(USER_ID);
    }

    @Test
    void requestDeletionIsIdempotent() {
        User u = user(AccountStatus.PENDING_DELETION);
        u.setDeletionRequestedAt(Instant.now().minus(2, ChronoUnit.DAYS));
        when(users.findById(USER_ID)).thenReturn(Optional.of(u));

        svc().requestDeletion(USER_ID);

        verify(users, never()).save(any());
        verifyNoInteractions(cache);
        verifyNoInteractions(revocations);
    }

    @Test
    void purgeExpiredDeletionsHardDeletesEachDueAccount() {
        User due = user(AccountStatus.PENDING_DELETION);
        when(users.findByDeletionRequestedAtBefore(any())).thenReturn(List.of(due));

        int purged = svc().purgeExpiredDeletions();

        assertEquals(1, purged);
        verify(items).deleteByUserId(USER_ID);
        verify(notifications).deleteByUserId(USER_ID);
        verify(locations).deleteByUserId(USER_ID);
        verify(cache).delete("session:" + USER_ID);
        verify(users).delete(due);

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(users).findByDeletionRequestedAtBefore(cutoff.capture());
        assertTrue(cutoff.getValue().isBefore(Instant.now().minus(29, ChronoUnit.DAYS)));
    }

    @Test
    void purgeExpiredDeletionsNoOpWhenNothingDue() {
        when(users.findByDeletionRequestedAtBefore(any())).thenReturn(List.of());

        assertEquals(0, svc().purgeExpiredDeletions());
        verify(users, never()).delete(any(User.class));
    }

    @Test
    void exportCsvContainsProfileAndInventorySections() {
        User u = user(AccountStatus.ACTIVE);
        u.applyName("Ravi", "Kumar");
        u.setShopName("Ravi Auto, Parts");
        u.setCreatedAt(Instant.now());
        when(users.findById(USER_ID)).thenReturn(Optional.of(u));
        when(locations.findByUserId(USER_ID)).thenReturn(Optional.empty());

        InventoryItem item = new InventoryItem();
        item.setPartName("Brake Pad");
        item.setQuantity(4);
        item.setMinQuantity(2);
        when(items.findByUserIdOrderByCreatedAtAsc(USER_ID)).thenReturn(List.of(item));

        String csv = svc().exportCsv(USER_ID);

        assertTrue(csv.contains("PROFILE"));
        assertTrue(csv.contains("phone,8619544044"));
        assertTrue(csv.contains("\"Ravi Auto, Parts\""), "comma value must be quoted");
        assertTrue(csv.contains("INVENTORY,1"));
        assertTrue(csv.contains("Brake Pad"));
        assertTrue(csv.contains("IN_STOCK"));
    }
}
