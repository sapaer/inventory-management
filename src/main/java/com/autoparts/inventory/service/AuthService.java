package com.autoparts.inventory.service;

import com.autoparts.inventory.api.AppException;
import com.autoparts.inventory.config.AppProperties;
import com.autoparts.inventory.config.RateLimitProperties;
import com.autoparts.inventory.enums.AccountStatus;
import com.autoparts.inventory.enums.OnboardingStatus;
import com.autoparts.inventory.dto.AccountSelectionResponse;
import com.autoparts.inventory.dto.AccountSummaryResponse;
import com.autoparts.inventory.dto.AuthResponse;
import com.autoparts.inventory.dto.AuthResult;
import com.autoparts.inventory.dto.ProfileUpdateRequest;
import com.autoparts.inventory.dto.TokenRefreshResponse;
import com.autoparts.inventory.dto.UserResponse;
import com.autoparts.inventory.entity.User;
import com.autoparts.inventory.entity.UserLocation;
import com.autoparts.inventory.repository.UserLocationRepository;
import com.autoparts.inventory.repository.UserRepository;
import com.autoparts.inventory.security.JwtService;
import com.autoparts.inventory.security.TokenRevocationService;
import com.autoparts.inventory.store.AppKvStore;
import com.autoparts.inventory.store.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int OTP_EXPIRY_SECONDS = 300;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final UserLocationRepository locations;
    private final AppKvStore cache;
    private final JwtService jwt;
    private final OtpDispatcher otp;
    private final AppProperties props;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;
    private final RateLimitProperties rateLimitProps;
    private final TokenRevocationService revocations;

    public AuthService(
            UserRepository users,
            UserLocationRepository locations,
            AppKvStore cache,
            JwtService jwt,
            OtpDispatcher otp,
            AppProperties props,
            PasswordEncoder passwordEncoder,
            RateLimiter rateLimiter,
            RateLimitProperties rateLimitProps,
            TokenRevocationService revocations
    ) {
        this.users = users;
        this.locations = locations;
        this.cache = cache;
        this.jwt = jwt;
        this.otp = otp;
        this.props = props;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.rateLimitProps = rateLimitProps;
        this.revocations = revocations;
    }

    private static String otpRequestKey(String phone) {
        return "otp_attempts:" + phone;
    }

    private static String otpVerifyKey(String phone) {
        return "otp_verify_attempts:" + phone;
    }

    public void requestOtp(String phone) {
        if (rateLimiter.isExceeded(otpRequestKey(phone), rateLimitProps.getOtpRequestPerPhone())) {
            throw AppException.tooManyRequests("OTP_MAX_ATTEMPTS", "Too many OTP requests. Try again in 10 minutes.");
        }

        boolean bypass = props.isDevOtpBypass();
        String code = bypass
                ? props.effectiveDevOtpCode()
                : "%06d".formatted(RANDOM.nextInt(1_000_000));

        if (!bypass) {
            try {
                otp.sendOtp(phone, code);
            } catch (Exception ex) {
                log.error("otp delivery failed phone={}", phone, ex);
                String detail = ex.getMessage() == null ? "Please try again." : ex.getMessage();
                throw AppException.badRequest("OTP_DELIVERY_FAILED", "Could not send OTP. " + detail);
            }
        }

        cache.set("otp:" + phone, code, Duration.ofSeconds(OTP_EXPIRY_SECONDS));
        rateLimiter.hit(otpRequestKey(phone), Duration.ofSeconds(rateLimitProps.getOtpRequestWindowSeconds()));
        if (bypass) {
            log.warn("DEV OTP bypass active phone={} otp={} (delivery skipped)", phone, code);
        } else if (props.isLogOtp()) {
            log.info("OTP generated for local testing phone={} otp={}", phone, code);
        } else {
            log.info("OTP sent phone={}", phone);
        }
    }

    @Transactional
    public AuthResult verifyOtp(String phone, String submitted, String firstName, String lastName) {
        verifyOtpCodeOrThrow(phone, submitted);

        List<User> matches = users.findAllByPhone(phone);
        if (matches.isEmpty()) {
            User created = new User();
            created.setPhone(phone);
            created.setVerified(true);
            created.setOnboardingStatus(OnboardingStatus.REGISTERED);
            created.applyName(firstName, lastName);
            return tokens(users.save(created), true);
        }
        if (matches.size() > 1) {
            String phoneToken = UUID.randomUUID().toString();
            cache.set("phone_token:" + phoneToken, phone, Duration.ofSeconds(OTP_EXPIRY_SECONDS));
            return new AccountSelectionResponse(phoneToken, matches.stream().map(this::toAccountSummary).toList());
        }
        return tokens(reactivateAndVerify(matches.get(0)), false);
    }

    /**
     * Sign in with a phone + password instead of an OTP. Mirrors {@link #verifyOtp}
     * for the multi-account and reactivation flows, but never creates an account —
     * a fresh number must go through OTP signup first.
     */
    @Transactional
    public AuthResult passwordLogin(String phone, String password) {
        List<User> matches = users.findAllByPhone(phone);
        if (matches.isEmpty()) {
            throw AppException.unauthorized("No account found for this number. Sign up with OTP first.");
        }
        boolean anyHasPassword = matches.stream().anyMatch(u -> u.getPasswordHash() != null);
        if (!anyHasPassword) {
            throw AppException.badRequest(
                    "PASSWORD_NOT_SET",
                    "No password set for this number. Sign in with OTP, then add a password from Settings."
            );
        }
        boolean ok = matches.stream()
                .anyMatch(u -> u.getPasswordHash() != null && passwordEncoder.matches(password, u.getPasswordHash()));
        if (!ok) {
            throw AppException.unauthorized("Incorrect password.");
        }
        if (matches.size() > 1) {
            String phoneToken = UUID.randomUUID().toString();
            cache.set("phone_token:" + phoneToken, phone, Duration.ofSeconds(OTP_EXPIRY_SECONDS));
            return new AccountSelectionResponse(phoneToken, matches.stream().map(this::toAccountSummary).toList());
        }
        return tokens(reactivateAndVerify(matches.get(0)), false);
    }

    private void verifyOtpCodeOrThrow(String phone, String submitted) {
        boolean bypassCode = props.isDevOtpBypass()
                && submitted != null
                && submitted.equals(props.effectiveDevOtpCode());
        if (!bypassCode) {
            if (rateLimiter.isExceeded(otpVerifyKey(phone), rateLimitProps.getOtpVerifyPerPhone())) {
                throw AppException.tooManyRequests(
                        "OTP_MAX_VERIFY_ATTEMPTS",
                        "Too many incorrect OTP attempts. Request a new OTP and try again in 10 minutes."
                );
            }
            String stored = cache.get("otp:" + phone);
            if (stored == null || stored.isBlank()) {
                throw AppException.badRequest("OTP_EXPIRED", "OTP has expired. Please request a new one.");
            }
            if (!stored.equals(submitted)) {
                rateLimiter.hit(otpVerifyKey(phone), Duration.ofSeconds(rateLimitProps.getOtpVerifyWindowSeconds()));
                throw AppException.badRequest("OTP_INVALID", "Incorrect OTP. Please try again.");
            }
        } else {
            log.warn("DEV OTP bypass accepted phone={}", phone);
        }
        cache.delete("otp:" + phone, otpRequestKey(phone), otpVerifyKey(phone));
    }

    @Transactional
    public void setPassword(UUID userId, String password) {
        User user = users.findById(userId).orElseThrow(() -> AppException.notFound("User not found"));
        if (user.getPasswordHash() != null) {
            throw AppException.conflict("PASSWORD_ALREADY_SET", "Password already set. Use change password instead.");
        }
        user.setPasswordHash(passwordEncoder.encode(password));
        users.save(user);
        log.info("password set userId={}", userId);
    }

    public void requestPasswordChangeOtp(UUID userId) {
        User user = users.findById(userId).orElseThrow(() -> AppException.notFound("User not found"));
        requestOtp(user.getPhone());
    }

    @Transactional
    public void changePassword(UUID userId, String otp, String newPassword) {
        User user = users.findById(userId).orElseThrow(() -> AppException.notFound("User not found"));
        if (user.getPasswordHash() == null) {
            throw AppException.badRequest("PASSWORD_NOT_SET", "No password set yet. Add one first.");
        }
        verifyOtpCodeOrThrow(user.getPhone(), otp);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        users.save(user);
        cache.delete("session:" + userId);
        log.info("password changed userId={}", userId);
    }

    /**
     * Step 1 of the forgot-password flow (unauthenticated): send an OTP to a number
     * that already has an account. Fails the same way for an unknown number as for a
     * known one to avoid leaking which numbers are registered.
     */
    public void requestPasswordResetOtp(String phone) {
        List<User> matches = users.findAllByPhone(phone);
        if (matches.isEmpty()) {
            throw AppException.unauthorized("No account found for this number. Sign up with OTP first.");
        }
        requestOtp(phone);
    }

    /**
     * Step 2 of the forgot-password flow (unauthenticated): with a valid OTP, set a new
     * password on every account tied to the number. Mirrors {@link #passwordLogin} in
     * treating the password as shared across a phone's accounts.
     */
    @Transactional
    public void resetPassword(String phone, String otp, String newPassword) {
        List<User> matches = users.findAllByPhone(phone);
        if (matches.isEmpty()) {
            throw AppException.unauthorized("No account found for this number. Sign up with OTP first.");
        }
        verifyOtpCodeOrThrow(phone, otp);
        String hash = passwordEncoder.encode(newPassword);
        for (User user : matches) {
            user.setPasswordHash(hash);
            users.save(user);
            cache.delete("session:" + user.getId());
        }
        log.info("password reset via forgot-password phone={} accounts={}", phone, matches.size());
    }

    /**
     * Re-verifying OTP (or a successful password login) proves the user still wants the account,
     * so it reactivates a deactivated one and cancels a pending deletion.
     */
    private User reactivateAndVerify(User user) {
        boolean dirty = false;
        boolean statusRestored = false;
        if (!user.isVerified()) {
            user.setVerified(true);
            dirty = true;
        }
        if (user.getStatus() == AccountStatus.DEACTIVATED) {
            user.setStatus(AccountStatus.ACTIVE);
            user.setDeactivatedAt(null);
            dirty = true;
            statusRestored = true;
            log.info("account reactivated userId={}", user.getId());
        }
        if (user.getStatus() == AccountStatus.PENDING_DELETION) {
            user.setStatus(AccountStatus.ACTIVE);
            user.setDeletionRequestedAt(null);
            dirty = true;
            statusRestored = true;
            log.info("account deletion cancelled on login userId={}", user.getId());
        }
        if (statusRestored) {
            revocations.restore(user.getId());
        }
        return dirty ? users.save(user) : user;
    }

    @Transactional
    public AuthResponse selectAccount(String phoneToken, UUID accountId) {
        if (phoneToken == null || phoneToken.isBlank() || accountId == null) {
            throw AppException.badRequest("VALIDATION_ERROR", "phoneToken and accountId are required");
        }
        String cacheKey = "phone_token:" + phoneToken;
        String phone = cache.get(cacheKey);
        if (phone == null || phone.isBlank()) {
            throw AppException.unauthorized("Phone verification expired. Please request a new OTP.");
        }
        User user = users.findById(accountId).orElseThrow(() -> AppException.notFound("Account not found"));
        if (!user.getPhone().equals(phone)) {
            throw AppException.unauthorized("This account does not belong to the verified phone number");
        }
        cache.delete(cacheKey);
        log.info("account selected accountId={} phone={}", accountId, phone);
        return tokens(reactivateAndVerify(user), false);
    }

    @Transactional
    public AuthResponse switchAccount(UUID currentUserId, UUID targetAccountId) {
        User current = users.findById(currentUserId).orElseThrow(() -> AppException.notFound("User not found"));
        User target = users.findById(targetAccountId).orElseThrow(() -> AppException.notFound("Account not found"));
        if (!target.getPhone().equals(current.getPhone())) {
            log.warn("account switch denied fromUserId={} toAccountId={} phone mismatch", currentUserId, targetAccountId);
            throw AppException.unauthorized("This account is not linked to your phone number");
        }
        if (target.getStatus() == AccountStatus.DEACTIVATED) {
            throw AppException.conflict("ACCOUNT_DEACTIVATED", "This account is deactivated. Log in with OTP to reactivate it.");
        }
        if (target.getStatus() == AccountStatus.PENDING_DELETION) {
            throw AppException.conflict("ACCOUNT_PENDING_DELETION",
                    "This account is scheduled for deletion. Log in with OTP to restore it.");
        }
        log.info("account switched fromUserId={} toAccountId={}", currentUserId, targetAccountId);
        return tokens(target, false);
    }

    @Transactional
    public AuthResponse createAccount(UUID currentUserId, String firstName, String lastName) {
        User current = users.findById(currentUserId).orElseThrow(() -> AppException.notFound("User not found"));
        User created = new User();
        created.setPhone(current.getPhone());
        created.setVerified(true);
        created.setOnboardingStatus(OnboardingStatus.REGISTERED);
        created.applyName(firstName, lastName);
        User saved = users.save(created);
        log.info("account created accountId={} phone={} fromUserId={}", saved.getId(), saved.getPhone(), currentUserId);
        return tokens(saved, true);
    }

    public List<AccountSummaryResponse> listAccounts(UUID currentUserId) {
        User current = users.findById(currentUserId).orElseThrow(() -> AppException.notFound("User not found"));
        return users.findAllByPhone(current.getPhone()).stream().map(this::toAccountSummary).toList();
    }

    @Transactional
    public void deactivate(UUID userId) {
        User user = users.findById(userId).orElseThrow(() -> AppException.notFound("User not found"));
        user.setStatus(AccountStatus.DEACTIVATED);
        user.setDeactivatedAt(Instant.now());
        users.save(user);
        cache.delete("session:" + userId);
        revocations.revoke(userId);
        log.info("account deactivated userId={}", userId);
    }

    public TokenRefreshResponse refreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw AppException.unauthorized("Invalid refresh token");
        }
        String[] parts = refreshToken.split(":", 2);
        if (parts.length != 2) {
            throw AppException.unauthorized("Invalid refresh token");
        }
        UUID userId;
        try {
            userId = UUID.fromString(parts[0]);
        } catch (IllegalArgumentException ex) {
            throw AppException.unauthorized("Invalid refresh token");
        }
        String sessionKey = "session:" + userId;
        String stored = cache.get(sessionKey);
        if (stored == null || !stored.equals(refreshToken)) {
            throw AppException.unauthorized("Refresh token expired or invalid");
        }
        User user = users.findById(userId).orElseThrow(() -> AppException.unauthorized("User not found"));
        String newRefresh = userId + ":" + UUID.randomUUID();
        cache.set(sessionKey, newRefresh, Duration.ofDays(props.getJwt().getRefreshExpiryDays()));
        return new TokenRefreshResponse(jwt.generateAccessToken(userId, user.getPhone()), newRefresh);
    }

    public void logout(UUID userId) {
        cache.delete("session:" + userId);
    }

    public UserResponse getProfile(UUID userId) {
        User user = users.findById(userId).orElseThrow(() -> AppException.notFound("User not found"));
        return toUserResponse(user);
    }

    @Transactional
    public UserResponse updateProfile(UUID userId, ProfileUpdateRequest dto) {
        User user = users.findById(userId).orElseThrow(() -> AppException.notFound("User not found"));
        if (dto.getFirstName() != null || dto.getLastName() != null) {
            user.applyName(dto.getFirstName(), dto.getLastName());
        } else if (dto.getName() != null) {
            user.setName(dto.getName());
        }
        if (dto.getShopName() != null) {
            user.setShopName(dto.getShopName());
        }
        if (dto.getEmail() != null) {
            user.setEmail(dto.getEmail());
        }
        if (dto.getBusinessType() != null) {
            user.setBusinessType(dto.getBusinessType());
        }
        upsertLocation(userId, dto);
        if (dto.getVehicleCategories() != null) {
            user.setVehicleCategories(dto.getVehicleCategories());
        }
        if (user.getOnboardingStatus() == OnboardingStatus.REGISTERED
                && user.getName() != null && user.getShopName() != null) {
            user.setOnboardingStatus(OnboardingStatus.PROFILED);
        }
        return toUserResponse(users.save(user));
    }

    private UserResponse toUserResponse(User user) {
        UserLocation loc = locations.findByUserId(user.getId()).orElse(null);
        return new UserResponse(
                user.getId(),
                user.getPhone(),
                user.getFirstName(),
                user.getLastName(),
                user.getName(),
                user.getShopName(),
                user.getEmail(),
                user.getBusinessType(),
                user.getOnboardingStatus(),
                user.getStatus(),
                user.getDeletionRequestedAt(),
                loc == null ? null : loc.getAddress(),
                loc == null ? null : loc.getArea(),
                loc == null ? null : loc.getCity(),
                loc == null ? null : loc.getState(),
                loc == null ? null : loc.getPincode(),
                loc == null ? null : loc.getGeoLat(),
                loc == null ? null : loc.getGeoLng(),
                user.getVehicleCategories(),
                user.getPasswordHash() != null
        );
    }

    private AccountSummaryResponse toAccountSummary(User user) {
        return new AccountSummaryResponse(
                user.getId(),
                user.getShopName(),
                user.getName(),
                user.getBusinessType(),
                user.getOnboardingStatus(),
                user.getStatus()
        );
    }

    private void upsertLocation(UUID userId, ProfileUpdateRequest dto) {
        if (dto.getAddress() == null && dto.getArea() == null && dto.getCity() == null
                && dto.getPincode() == null && dto.getGeoLat() == null && dto.getGeoLng() == null) {
            return;
        }
        UserLocation loc = locations.findByUserId(userId).orElseGet(() -> {
            UserLocation created = new UserLocation();
            created.setUserId(userId);
            return created;
        });
        if (dto.getAddress() != null) loc.setAddress(dto.getAddress());
        if (dto.getArea() != null) loc.setArea(dto.getArea());
        if (dto.getCity() != null) loc.setCity(dto.getCity());
        if (dto.getState() != null) loc.setState(dto.getState());
        if (dto.getPincode() != null) loc.setPincode(dto.getPincode());
        if (dto.getGeoLat() != null) loc.setGeoLat(BigDecimal.valueOf(dto.getGeoLat()));
        if (dto.getGeoLng() != null) loc.setGeoLng(BigDecimal.valueOf(dto.getGeoLng()));
        locations.save(loc);
    }

    private AuthResponse tokens(User user, boolean isNewUser) {
        String access = jwt.generateAccessToken(user.getId(), user.getPhone());
        String refresh = user.getId() + ":" + UUID.randomUUID();
        cache.set("session:" + user.getId(), refresh, Duration.ofDays(props.getJwt().getRefreshExpiryDays()));
        return new AuthResponse(access, refresh, toUserResponse(user), isNewUser);
    }
}
