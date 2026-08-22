package com.autoparts.inventory.controller;

import com.autoparts.inventory.api.ApiEnvelope;
import com.autoparts.inventory.api.AppException;
import com.autoparts.inventory.dto.AccountSelectRequest;
import com.autoparts.inventory.dto.AccountSummaryResponse;
import com.autoparts.inventory.dto.AccountSwitchRequest;
import com.autoparts.inventory.dto.AuthResponse;
import com.autoparts.inventory.dto.AuthResult;
import com.autoparts.inventory.dto.OtpRequest;
import com.autoparts.inventory.dto.OtpRequestedResponse;
import com.autoparts.inventory.dto.OtpVerifyRequest;
import com.autoparts.inventory.dto.ProfileUpdateRequest;
import com.autoparts.inventory.dto.RefreshTokenRequest;
import com.autoparts.inventory.dto.TokenRefreshResponse;
import com.autoparts.inventory.dto.UserResponse;
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

import java.util.List;
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
    public ResponseEntity<ApiEnvelope<OtpRequestedResponse>> requestOtp(@Valid @RequestBody OtpRequest req) {
        validatePhone(req.getPhone());
        authService.requestOtp(req.getPhone());
        return ResponseEntity.ok(ApiEnvelope.ok(new OtpRequestedResponse("OTP sent", "300")));
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<ApiEnvelope<AuthResult>> verifyOtp(@Valid @RequestBody OtpVerifyRequest req) {
        validatePhone(req.getPhone());
        return ResponseEntity.ok(ApiEnvelope.ok(authService.verifyOtp(req.getPhone(), req.getOtp())));
    }

    @PostMapping("/token/refresh")
    public ResponseEntity<ApiEnvelope<TokenRefreshResponse>> refresh(@RequestBody RefreshTokenRequest req) {
        return ResponseEntity.ok(ApiEnvelope.ok(authService.refreshToken(req.getRefreshToken())));
    }

    @DeleteMapping("/logout")
    public ResponseEntity<ApiEnvelope<Void>> logout(@AuthenticationPrincipal UUID userId) {
        authService.logout(userId);
        return ResponseEntity.ok(ApiEnvelope.ok(null));
    }

    @GetMapping("/profile")
    public ResponseEntity<ApiEnvelope<UserResponse>> profile(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(ApiEnvelope.ok(authService.getProfile(userId)));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiEnvelope<UserResponse>> updateProfile(
            @AuthenticationPrincipal UUID userId,
            @RequestBody ProfileUpdateRequest dto
    ) {
        return ResponseEntity.ok(ApiEnvelope.ok(authService.updateProfile(userId, dto)));
    }

    @PostMapping("/accounts/select")
    public ResponseEntity<ApiEnvelope<AuthResponse>> selectAccount(@RequestBody AccountSelectRequest req) {
        return ResponseEntity.ok(ApiEnvelope.ok(authService.selectAccount(req.getPhoneToken(), req.getAccountId())));
    }

    @PostMapping("/accounts/switch")
    public ResponseEntity<ApiEnvelope<AuthResponse>> switchAccount(
            @AuthenticationPrincipal UUID userId,
            @RequestBody AccountSwitchRequest req
    ) {
        return ResponseEntity.ok(ApiEnvelope.ok(authService.switchAccount(userId, req.getAccountId())));
    }

    @GetMapping("/accounts")
    public ResponseEntity<ApiEnvelope<List<AccountSummaryResponse>>> listAccounts(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(ApiEnvelope.ok(authService.listAccounts(userId)));
    }

    @PostMapping("/accounts")
    public ResponseEntity<ApiEnvelope<AuthResponse>> createAccount(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(ApiEnvelope.ok(authService.createAccount(userId)));
    }

    @PostMapping("/deactivate")
    public ResponseEntity<ApiEnvelope<Void>> deactivate(@AuthenticationPrincipal UUID userId) {
        authService.deactivate(userId);
        return ResponseEntity.ok(ApiEnvelope.ok(null));
    }

    @DeleteMapping("/account")
    public ResponseEntity<ApiEnvelope<Void>> deleteAccount(@AuthenticationPrincipal UUID userId) {
        authService.deleteAccount(userId);
        return ResponseEntity.ok(ApiEnvelope.ok(null));
    }

    private static void validatePhone(String phone) {
        if (!INDIAN_PHONE.matcher(phone).matches()) {
            throw AppException.badRequest("VALIDATION_ERROR", "Enter a valid 10-digit Indian mobile number");
        }
    }
}
