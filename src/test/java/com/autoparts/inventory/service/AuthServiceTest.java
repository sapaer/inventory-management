package com.autoparts.inventory.service;

import com.autoparts.inventory.api.AppException;
import com.autoparts.inventory.config.AppProperties;
import com.autoparts.inventory.config.AwsProperties;
import com.autoparts.inventory.config.GoogleProperties;
import com.autoparts.inventory.config.JwtProperties;
import com.autoparts.inventory.config.SmsProperties;
import com.autoparts.inventory.config.TwilioProperties;
import com.autoparts.inventory.config.WhatsAppProperties;
import com.autoparts.inventory.dto.AuthResponse;
import com.autoparts.inventory.repository.UserLocationRepository;
import com.autoparts.inventory.repository.UserRepository;
import com.autoparts.inventory.security.JwtService;
import com.autoparts.inventory.store.AppKvStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock UserRepository users;
    @Mock UserLocationRepository locations;
    @Mock AppKvStore cache;
    @Mock JwtService jwt;
    @Mock OtpDispatcher otp;

    private static AppProperties props(boolean bypass) {
        return new AppProperties(
                true,
                bypass,
                "000000",
                new JwtProperties("QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=", 24, 30),
                new AwsProperties("b", "ap-south-1", "k", "s", "http://localhost"),
                new WhatsAppProperties("https://graph.facebook.com/v25.0", "", ""),
                new SmsProperties("twilio", "", ""),
                new TwilioProperties("", "", "", "", "", ""),
                new GoogleProperties("")
        );
    }

    @Test
    void requestOtpDoesNotStoreWhenDeliveryFails() {
        AuthService svc = new AuthService(users, locations, cache, jwt, otp, props(false));
        doThrow(new IllegalStateException("no otp delivery channel available")).when(otp).sendOtp(anyString(), anyString());

        AppException ex = assertThrows(AppException.class, () -> svc.requestOtp("8619544044"));
        assertEquals("OTP_DELIVERY_FAILED", ex.getCode());
        verify(cache, never()).set(eq("otp:8619544044"), anyString(), org.mockito.ArgumentMatchers.any());
        verify(users, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void requestOtpBypassSkipsDeliveryAndStoresFixedCode() {
        AuthService svc = new AuthService(users, locations, cache, jwt, otp, props(true));

        svc.requestOtp("8619544044");

        verify(otp, never()).sendOtp(anyString(), anyString());
        verify(cache).set(eq("otp:8619544044"), eq("000000"), eq(Duration.ofSeconds(300)));
    }

    @Test
    void verifyOtpBypassAcceptsDevCode() {
        AuthService svc = new AuthService(users, locations, cache, jwt, otp, props(true));
        org.mockito.Mockito.when(users.findAllByPhone("8619544044")).thenReturn(java.util.List.of());
        org.mockito.Mockito.when(users.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> {
            com.autoparts.inventory.entity.User created = inv.getArgument(0);
            created.setId(java.util.UUID.fromString("11111111-1111-1111-1111-111111111111"));
            return created;
        });
        org.mockito.Mockito.when(jwt.generateAccessToken(org.mockito.ArgumentMatchers.any(), eq("8619544044")))
                .thenReturn("access");
        org.mockito.Mockito.when(locations.findByUserId(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.empty());

        AuthResponse out = (AuthResponse) svc.verifyOtp("8619544044", "000000");

        assertEquals(true, out.isNewUser());
        assertEquals("access", out.getAccessToken());
        verify(cache).delete("otp:8619544044", "otp_attempts:8619544044");
        verify(cache, never()).get("otp:8619544044");
    }
}
