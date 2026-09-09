package com.autoparts.inventory.controller;

import com.autoparts.inventory.api.ApiEnvelope;
import com.autoparts.inventory.api.AppException;
import com.autoparts.inventory.dto.AccountDeletionResponse;
import com.autoparts.inventory.dto.AccountSelectRequest;
import com.autoparts.inventory.dto.AccountSummaryResponse;
import com.autoparts.inventory.dto.AccountSwitchRequest;
import com.autoparts.inventory.dto.AuthResponse;
import com.autoparts.inventory.dto.AuthResult;
import com.autoparts.inventory.dto.ChangePasswordRequest;
import com.autoparts.inventory.dto.CreateAccountRequest;
import com.autoparts.inventory.dto.ForgotPasswordRequest;
import com.autoparts.inventory.dto.ForgotPasswordResetRequest;
import com.autoparts.inventory.dto.OtpRequest;
import com.autoparts.inventory.dto.OtpRequestedResponse;
import com.autoparts.inventory.dto.OtpVerifyRequest;
import com.autoparts.inventory.dto.PasswordLoginRequest;
import com.autoparts.inventory.dto.ProfileUpdateRequest;
import com.autoparts.inventory.dto.RefreshTokenRequest;
import com.autoparts.inventory.dto.SetPasswordRequest;
import com.autoparts.inventory.dto.TokenRefreshResponse;
import com.autoparts.inventory.dto.UserResponse;
import com.autoparts.inventory.service.AccountService;
import com.autoparts.inventory.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private static final Pattern INDIAN_PHONE = Pattern.compile("^[6-9]\\d{9}$");
    private final AuthService authService;
    private final AccountService accountService;

    public AuthController(AuthService authService, AccountService accountService) {
        this.authService = authService;
        this.accountService = accountService;
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
        return ResponseEntity.ok(ApiEnvelope.ok(
                authService.verifyOtp(req.getPhone(), req.getOtp(), req.getFirstName(), req.getLastName())));
    }

    @PostMapping("/password/login")
    public ResponseEntity<ApiEnvelope<AuthResult>> passwordLogin(@Valid @RequestBody PasswordLoginRequest req) {
        validatePhone(req.getPhone());
        return ResponseEntity.ok(ApiEnvelope.ok(authService.passwordLogin(req.getPhone(), req.getPassword())));
    }

    @PostMapping("/password/forgot/request")
    public ResponseEntity<ApiEnvelope<OtpRequestedResponse>> requestPasswordReset(
            @Valid @RequestBody ForgotPasswordRequest req
    ) {
        validatePhone(req.getPhone());
        authService.requestPasswordResetOtp(req.getPhone());
        return ResponseEntity.ok(ApiEnvelope.ok(new OtpRequestedResponse("OTP sent", "300")));
    }

    @PostMapping("/password/forgot/reset")
    public ResponseEntity<ApiEnvelope<Void>> resetPassword(@Valid @RequestBody ForgotPasswordResetRequest req) {
        validatePhone(req.getPhone());
        authService.resetPassword(req.getPhone(), req.getOtp(), req.getNewPassword());
        return ResponseEntity.ok(ApiEnvelope.ok(null));
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
    public ResponseEntity<ApiEnvelope<AuthResponse>> createAccount(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody(required = false) CreateAccountRequest req
    ) {
        String firstName = req == null ? null : req.getFirstName();
        String lastName = req == null ? null : req.getLastName();
        return ResponseEntity.ok(ApiEnvelope.ok(authService.createAccount(userId, firstName, lastName)));
    }

    @PostMapping("/password")
    public ResponseEntity<ApiEnvelope<Void>> setPassword(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody SetPasswordRequest req
    ) {
        authService.setPassword(userId, req.getPassword());
        return ResponseEntity.ok(ApiEnvelope.ok(null));
    }

    @PostMapping("/password/change/request")
    public ResponseEntity<ApiEnvelope<OtpRequestedResponse>> requestPasswordChangeOtp(@AuthenticationPrincipal UUID userId) {
        authService.requestPasswordChangeOtp(userId);
        return ResponseEntity.ok(ApiEnvelope.ok(new OtpRequestedResponse("OTP sent", "300")));
    }

    @PutMapping("/password")
    public ResponseEntity<ApiEnvelope<Void>> changePassword(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody ChangePasswordRequest req
    ) {
        authService.changePassword(userId, req.getOtp(), req.getNewPassword());
        return ResponseEntity.ok(ApiEnvelope.ok(null));
    }

    @PostMapping("/deactivate")
    public ResponseEntity<ApiEnvelope<Void>> deactivate(@AuthenticationPrincipal UUID userId) {
        authService.deactivate(userId);
        return ResponseEntity.ok(ApiEnvelope.ok(null));
    }

    @GetMapping("/account/export")
    public ResponseEntity<String> exportAccountData(@AuthenticationPrincipal UUID userId) {
        String csv = accountService.exportCsv(userId);
        String filename = "account-export-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
    }

    @DeleteMapping("/account")
    public ResponseEntity<ApiEnvelope<AccountDeletionResponse>> deleteAccount(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(ApiEnvelope.ok(accountService.requestDeletion(userId)));
    }

    private static void validatePhone(String phone) {
        if (!INDIAN_PHONE.matcher(phone).matches()) {
            throw AppException.badRequest("VALIDATION_ERROR", "Enter a valid 10-digit Indian mobile number");
        }
    }
}
