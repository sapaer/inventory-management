package com.autoparts.inventory.service;

import com.autoparts.inventory.api.AppException;
import com.autoparts.inventory.config.AppProperties;
import com.autoparts.inventory.repository.UserLocationRepository;
import com.autoparts.inventory.repository.UserRepository;
import com.autoparts.inventory.repository.UserVehicleCategoryRepository;
import com.autoparts.inventory.security.JwtService;
import com.autoparts.inventory.store.RedisCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock UserRepository users;
    @Mock UserLocationRepository locations;
    @Mock UserVehicleCategoryRepository vehicleCategories;
    @Mock RedisCache cache;
    @Mock JwtService jwt;
    @Mock OtpDispatcher otp;

    @Test
    void requestOtpDoesNotStoreWhenDeliveryFails() {
        AppProperties props = new AppProperties(
                true,
                new AppProperties.Jwt("QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=", 24, 30),
                new AppProperties.Aws("b", "ap-south-1", "k", "s", "http://localhost"),
                new AppProperties.WhatsApp("https://graph.facebook.com/v25.0", "", ""),
                new AppProperties.Sms("msg91", "", "", "", "", "")
        );
        AuthService svc = new AuthService(users, locations, vehicleCategories, cache, jwt, otp, props);
        doThrow(new IllegalStateException("no otp delivery channel available")).when(otp).sendOtp(anyString(), anyString());

        AppException ex = assertThrows(AppException.class, () -> svc.requestOtp("8619544044"));
        assertEquals("OTP_DELIVERY_FAILED", ex.getCode());
        verify(cache, never()).set(org.mockito.ArgumentMatchers.eq("otp:8619544044"), anyString(), org.mockito.ArgumentMatchers.any());
        verify(users, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
