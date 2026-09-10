package com.autoparts.inventory.service;

import com.autoparts.inventory.api.AppException;
import com.autoparts.inventory.config.AppProperties;
import com.autoparts.inventory.config.AwsProperties;
import com.autoparts.inventory.config.GoogleProperties;
import com.autoparts.inventory.config.JwtProperties;
import com.autoparts.inventory.config.RateLimitProperties;
import com.autoparts.inventory.config.SmsProperties;
import com.autoparts.inventory.config.TwilioProperties;
import com.autoparts.inventory.config.WhatsAppProperties;
import com.autoparts.inventory.dto.AuthResponse;
import com.autoparts.inventory.repository.UserLocationRepository;
import com.autoparts.inventory.repository.UserRepository;
import com.autoparts.inventory.security.JwtService;
import com.autoparts.inventory.security.TokenRevocationService;
import com.autoparts.inventory.store.AppKvStore;
import com.autoparts.inventory.store.RateLimiter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

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
    @Mock TokenRevocationService revocations;
    PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    RateLimitProperties rateLimitProps = new RateLimitProperties();

    private AuthService newSvc(boolean bypass) {
        return new AuthService(users, locations, cache, jwt, otp, props(bypass), passwordEncoder,
                new RateLimiter(cache), rateLimitProps, revocations);
    }

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
        AuthService svc = newSvc(false);
        doThrow(new IllegalStateException("no otp delivery channel available")).when(otp).sendOtp(anyString(), anyString());

        AppException ex = assertThrows(AppException.class, () -> svc.requestOtp("8619544044"));
        assertEquals("OTP_DELIVERY_FAILED", ex.getCode());
        verify(cache, never()).set(eq("otp:8619544044"), anyString(), org.mockito.ArgumentMatchers.any());
        verify(users, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void requestOtpBypassSkipsDeliveryAndStoresFixedCode() {
        AuthService svc = newSvc(true);

        svc.requestOtp("8619544044");

        verify(otp, never()).sendOtp(anyString(), anyString());
        verify(cache).set(eq("otp:8619544044"), eq("000000"), eq(Duration.ofSeconds(300)));
    }

    @Test
    void verifyOtpBypassAcceptsDevCode() {
        AuthService svc = newSvc(true);
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

        AuthResponse out = (AuthResponse) svc.verifyOtp("8619544044", "000000", "Ravi", "Kumar");

        assertEquals(true, out.isNewUser());
        assertEquals("access", out.getAccessToken());
        assertEquals("Ravi", out.getUser().getFirstName());
        assertEquals("Kumar", out.getUser().getLastName());
        assertEquals("Ravi Kumar", out.getUser().getName());
        verify(cache).delete("otp:8619544044", "otp_attempts:8619544044", "otp_verify_attempts:8619544044");
        verify(cache, never()).get("otp:8619544044");
    }

    private AuthService svc() {
        return newSvc(false);
    }

    private static com.autoparts.inventory.entity.User user(String phone, String passwordHash) {
        com.autoparts.inventory.entity.User u = new com.autoparts.inventory.entity.User();
        u.setId(java.util.UUID.fromString("22222222-2222-2222-2222-222222222222"));
        u.setPhone(phone);
        u.setVerified(true);
        u.setPasswordHash(passwordHash);
        return u;
    }

    @Test
    void passwordLoginRejectsUnknownNumber() {
        org.mockito.Mockito.when(users.findAllByPhone("8619544044")).thenReturn(java.util.List.of());

        AppException ex = assertThrows(AppException.class, () -> svc().passwordLogin("8619544044", "hunter22"));
        assertEquals("UNAUTHORIZED", ex.getCode());
    }

    @Test
    void passwordLoginRejectsWhenNoPasswordSet() {
        org.mockito.Mockito.when(users.findAllByPhone("8619544044"))
                .thenReturn(java.util.List.of(user("8619544044", null)));

        AppException ex = assertThrows(AppException.class, () -> svc().passwordLogin("8619544044", "hunter22"));
        assertEquals("PASSWORD_NOT_SET", ex.getCode());
    }

    @Test
    void passwordLoginRejectsWrongPassword() {
        org.mockito.Mockito.when(users.findAllByPhone("8619544044"))
                .thenReturn(java.util.List.of(user("8619544044", passwordEncoder.encode("correct-horse"))));

        AppException ex = assertThrows(AppException.class, () -> svc().passwordLogin("8619544044", "wrong-horse"));
        assertEquals("UNAUTHORIZED", ex.getCode());
    }

    @Test
    void passwordLoginIssuesTokensForCorrectPassword() {
        org.mockito.Mockito.when(users.findAllByPhone("8619544044"))
                .thenReturn(java.util.List.of(user("8619544044", passwordEncoder.encode("correct-horse"))));
        org.mockito.Mockito.when(jwt.generateAccessToken(org.mockito.ArgumentMatchers.any(), eq("8619544044")))
                .thenReturn("access");
        org.mockito.Mockito.when(locations.findByUserId(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.empty());

        AuthResponse out = (AuthResponse) svc().passwordLogin("8619544044", "correct-horse");

        assertEquals("access", out.getAccessToken());
        assertEquals(false, out.isNewUser());
    }

    @Test
    void passwordLoginCancelsAPendingDeletion() {
        com.autoparts.inventory.entity.User u = user("8619544044", passwordEncoder.encode("correct-horse"));
        u.setStatus(com.autoparts.inventory.enums.AccountStatus.PENDING_DELETION);
        u.setDeletionRequestedAt(java.time.Instant.now());
        org.mockito.Mockito.when(users.findAllByPhone("8619544044")).thenReturn(java.util.List.of(u));
        org.mockito.Mockito.when(users.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.when(jwt.generateAccessToken(org.mockito.ArgumentMatchers.any(), eq("8619544044")))
                .thenReturn("access");
        org.mockito.Mockito.when(locations.findByUserId(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.empty());

        svc().passwordLogin("8619544044", "correct-horse");

        assertEquals(com.autoparts.inventory.enums.AccountStatus.ACTIVE, u.getStatus());
        assertEquals(null, u.getDeletionRequestedAt());
        verify(revocations).restore(u.getId());
    }

    @Test
    void deactivateRevokesAlreadyIssuedAccessTokens() {
        com.autoparts.inventory.entity.User u = user("8619544044", null);
        org.mockito.Mockito.when(users.findById(u.getId())).thenReturn(java.util.Optional.of(u));

        newSvc(false).deactivate(u.getId());

        assertEquals(com.autoparts.inventory.enums.AccountStatus.DEACTIVATED, u.getStatus());
        verify(cache).delete("session:" + u.getId());
        verify(revocations).revoke(u.getId());
    }

    @Test
    void requestPasswordResetOtpRejectsUnknownNumber() {
        org.mockito.Mockito.when(users.findAllByPhone("8619544044")).thenReturn(java.util.List.of());

        AppException ex = assertThrows(AppException.class, () -> svc().requestPasswordResetOtp("8619544044"));
        assertEquals("UNAUTHORIZED", ex.getCode());
        verify(otp, never()).sendOtp(anyString(), anyString());
    }

    @Test
    void resetPasswordRejectsUnknownNumber() {
        org.mockito.Mockito.when(users.findAllByPhone("8619544044")).thenReturn(java.util.List.of());

        AppException ex = assertThrows(AppException.class,
                () -> svc().resetPassword("8619544044", "000000", "brand-new-pass"));
        assertEquals("UNAUTHORIZED", ex.getCode());
    }

    @Test
    void resetPasswordRejectsBadOtp() {
        org.mockito.Mockito.when(users.findAllByPhone("8619544044"))
                .thenReturn(java.util.List.of(user("8619544044", passwordEncoder.encode("old-pass"))));
        org.mockito.Mockito.lenient().when(cache.get("otp:8619544044")).thenReturn("123456");

        AppException ex = assertThrows(AppException.class,
                () -> svc().resetPassword("8619544044", "000000", "brand-new-pass"));
        assertEquals("OTP_INVALID", ex.getCode());
        verify(users, never()).save(org.mockito.ArgumentMatchers.any());
        verify(cache).incr("otp_verify_attempts:8619544044");
    }

    @Test
    void resetPasswordSetsNewHashAndClearsSessionOnValidOtp() {
        com.autoparts.inventory.entity.User u = user("8619544044", passwordEncoder.encode("old-pass"));
        org.mockito.Mockito.when(users.findAllByPhone("8619544044")).thenReturn(java.util.List.of(u));
        org.mockito.Mockito.lenient().when(cache.get("otp:8619544044")).thenReturn("000000");

        svc().resetPassword("8619544044", "000000", "brand-new-pass");

        assertEquals(true, passwordEncoder.matches("brand-new-pass", u.getPasswordHash()));
        verify(users).save(u);
        verify(cache).delete("session:" + u.getId());
    }

    @Test
    void requestOtpBlockedWhenPhoneRequestLimitReached() {
        org.mockito.Mockito.when(cache.get("otp_attempts:8619544044")).thenReturn("5");

        AppException ex = assertThrows(AppException.class, () -> newSvc(false).requestOtp("8619544044"));
        assertEquals("OTP_MAX_ATTEMPTS", ex.getCode());
        verify(otp, never()).sendOtp(anyString(), anyString());
        verify(cache, never()).set(eq("otp:8619544044"), anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void requestOtpCountsTheSendAgainstThePhoneWindow() {
        newSvc(true).requestOtp("8619544044");

        verify(cache).incr("otp_attempts:8619544044");
    }

    @Test
    void verifyOtpBlockedWhenPhoneVerifyLimitReached() {
        org.mockito.Mockito.when(cache.get("otp_verify_attempts:8619544044")).thenReturn("5");

        AppException ex = assertThrows(AppException.class,
                () -> newSvc(false).verifyOtp("8619544044", "123456", null, null));
        assertEquals("OTP_MAX_VERIFY_ATTEMPTS", ex.getCode());
        verify(cache, never()).get("otp:8619544044");
        verify(users, never()).findAllByPhone(anyString());
    }

    @Test
    void changePasswordSetsFirstPasswordWithValidOtp() {
        com.autoparts.inventory.entity.User u = user("8619544044", null);
        org.mockito.Mockito.when(users.findById(u.getId())).thenReturn(java.util.Optional.of(u));

        newSvc(true).changePassword(u.getId(), "000000", "brand-new-pass");

        org.junit.jupiter.api.Assertions.assertTrue(passwordEncoder.matches("brand-new-pass", u.getPasswordHash()));
        verify(users).save(u);
    }

    @Test
    void changePasswordUpdatesExistingPasswordWithValidOtp() {
        com.autoparts.inventory.entity.User u = user("8619544044", passwordEncoder.encode("old-pass"));
        org.mockito.Mockito.when(users.findById(u.getId())).thenReturn(java.util.Optional.of(u));

        newSvc(true).changePassword(u.getId(), "000000", "fresh-pass-99");

        org.junit.jupiter.api.Assertions.assertTrue(passwordEncoder.matches("fresh-pass-99", u.getPasswordHash()));
        verify(users).save(u);
    }
}
