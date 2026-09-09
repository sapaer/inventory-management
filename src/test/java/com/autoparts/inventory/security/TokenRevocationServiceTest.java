package com.autoparts.inventory.security;

import com.autoparts.inventory.config.AppProperties;
import com.autoparts.inventory.config.AwsProperties;
import com.autoparts.inventory.config.GoogleProperties;
import com.autoparts.inventory.config.JwtProperties;
import com.autoparts.inventory.config.SmsProperties;
import com.autoparts.inventory.config.TwilioProperties;
import com.autoparts.inventory.config.WhatsAppProperties;
import com.autoparts.inventory.store.AppKvStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenRevocationServiceTest {
    @Mock AppKvStore cache;

    private static final UUID USER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String KEY = "revoked_access:" + USER;

    private static AppProperties props() {
        return new AppProperties(true, false, "000000",
                new JwtProperties("QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=", 24, 30),
                new AwsProperties("b", "r", "k", "s", "u"),
                new WhatsAppProperties("u", "", ""),
                new SmsProperties("twilio", "", ""),
                new TwilioProperties("", "", "", "", "", ""),
                new GoogleProperties(""));
    }

    private TokenRevocationService svc() {
        return new TokenRevocationService(cache, props());
    }

    @Test
    void revokeWritesMarkerThatOutlivesTheAccessToken() {
        svc().revoke(USER);
        // accessExpiryHours (24) + 1
        verify(cache).set(KEY, "1", Duration.ofHours(25));
    }

    @Test
    void restoreClearsMarker() {
        svc().restore(USER);
        verify(cache).delete(KEY);
    }

    @Test
    void isRevokedReflectsMarkerPresence() {
        when(cache.existsActive(KEY)).thenReturn(true);
        assertTrue(svc().isRevoked(USER));

        when(cache.existsActive(KEY)).thenReturn(false);
        assertFalse(svc().isRevoked(USER));
    }
}
