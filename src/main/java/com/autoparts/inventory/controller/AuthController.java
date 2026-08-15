package com.autoparts.inventory.controller;

import com.autoparts.inventory.api.ApiEnvelope;
import com.autoparts.inventory.api.AppException;
import com.autoparts.inventory.dto.OtpRequest;
import com.autoparts.inventory.dto.OtpVerifyRequest;
import com.autoparts.inventory.dto.ProfileUpdateRequest;
import com.autoparts.inventory.dto.RefreshTokenRequest;
import com.autoparts.inventory.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private static final Pattern INDIAN_PHONE = Pattern.compile("^[6-9]\\d{9}$");
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/otp/request")
    public ResponseEntity<ApiEnvelope<Map<String, String>>> requestOtp(@Valid @RequestBody OtpRequest req) {
        validatePhone(req.phone());
        authService.requestOtp(req.phone());
        return ResponseEntity.ok(ApiEnvelope.ok(Map.of("message", "OTP sent", "expires_in", "300")));
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> verifyOtp(@Valid @RequestBody OtpVerifyRequest req) {
        validatePhone(req.phone());
        return ResponseEntity.ok(ApiEnvelope.ok(authService.verifyOtp(req.phone(), req.otp())));
    }

    @PostMapping("/token/refresh")
    public ResponseEntity<ApiEnvelope<Map<String, String>>> refresh(@RequestBody RefreshTokenRequest req) {
        return ResponseEntity.ok(ApiEnvelope.ok(authService.refreshToken(req.refresh_token())));
    }

    @DeleteMapping("/logout")
    public ResponseEntity<ApiEnvelope<Void>> logout(@AuthenticationPrincipal UUID userId) {
        authService.logout(userId);
        return ResponseEntity.ok(ApiEnvelope.ok(null));
    }

    @GetMapping("/profile")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> profile(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(ApiEnvelope.ok(authService.getProfile(userId)));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> updateProfile(
            @AuthenticationPrincipal UUID userId,
            @RequestBody ProfileUpdateRequest dto
    ) {
        return ResponseEntity.ok(ApiEnvelope.ok(authService.updateProfile(userId, dto)));
    }

    private static void validatePhone(String phone) {
        if (!INDIAN_PHONE.matcher(phone).matches()) {
            throw AppException.badRequest("VALIDATION_ERROR", "Enter a valid 10-digit Indian mobile number");
        }
    }
}
