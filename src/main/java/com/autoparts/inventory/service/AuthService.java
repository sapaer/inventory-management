package com.autoparts.inventory.service;

import com.autoparts.inventory.api.AppException;
import com.autoparts.inventory.config.AppProperties;
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
import com.autoparts.inventory.store.AppKvStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final int ATTEMPT_WINDOW_SECONDS = 600;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final UserLocationRepository locations;
    private final AppKvStore cache;
    private final JwtService jwt;
    private final OtpDispatcher otp;
    private final AppProperties props;

    public AuthService(
            UserRepository users,
            UserLocationRepository locations,
            AppKvStore cache,
            JwtService jwt,
            OtpDispatcher otp,
            AppProperties props
    ) {
        this.users = users;
        this.locations = locations;
        this.cache = cache;
        this.jwt = jwt;
        this.otp = otp;
        this.props = props;
    }

    public void requestOtp(String phone) {
        String attemptsKey = "otp_attempts:" + phone;
        String attempts = cache.get(attemptsKey);
        if (attempts != null && !attempts.isBlank()) {
            int n = Integer.parseInt(attempts);
            if (n >= MAX_OTP_ATTEMPTS) {
                throw AppException.tooManyRequests("OTP_MAX_ATTEMPTS", "Too many OTP requests. Try again in 10 minutes.");
            }
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
        cache.incr(attemptsKey);
        cache.expire(attemptsKey, Duration.ofSeconds(ATTEMPT_WINDOW_SECONDS));
        if (bypass) {
            log.warn("DEV OTP bypass active phone={} otp={} (delivery skipped)", phone, code);
        } else if (props.isLogOtp()) {
            log.info("OTP generated for local testing phone={} otp={}", phone, code);
        } else {
            log.info("OTP sent phone={}", phone);
        }
    }

    @Transactional
    public AuthResult verifyOtp(String phone, String submitted) {
        boolean bypassCode = props.isDevOtpBypass()
                && submitted != null
                && submitted.equals(props.effectiveDevOtpCode());
        if (!bypassCode) {
            String stored = cache.get("otp:" + phone);
            if (stored == null || stored.isBlank()) {
                throw AppException.badRequest("OTP_EXPIRED", "OTP has expired. Please request a new one.");
            }
            if (!stored.equals(submitted)) {
                throw AppException.badRequest("OTP_INVALID", "Incorrect OTP. Please try again.");
            }
        } else {
            log.warn("DEV OTP bypass accepted phone={}", phone);
        }
        cache.delete("otp:" + phone, "otp_attempts:" + phone);

        List<User> matches = users.findAllByPhone(phone);
        if (matches.isEmpty()) {
            User created = new User();
            created.setPhone(phone);
            created.setVerified(true);
            created.setOnboardingStatus(OnboardingStatus.REGISTERED);
            return tokens(users.save(created), true);
        }
        if (matches.size() > 1) {
            String phoneToken = UUID.randomUUID().toString();
            cache.set("phone_token:" + phoneToken, phone, Duration.ofSeconds(OTP_EXPIRY_SECONDS));
            return new AccountSelectionResponse(phoneToken, matches.stream().map(this::toAccountSummary).toList());
        }
        return tokens(reactivateAndVerify(matches.get(0)), false);
    }

    /** Re-verifying OTP proves phone ownership again, so it also reactivates a deactivated account. */
    private User reactivateAndVerify(User user) {
        boolean dirty = false;
        if (!user.isVerified()) {
            user.setVerified(true);
            dirty = true;
        }
        if (user.getStatus() == AccountStatus.DEACTIVATED) {
            user.setStatus(AccountStatus.ACTIVE);
            user.setDeactivatedAt(null);
            dirty = true;
            log.info("account reactivated userId={}", user.getId());
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
        log.info("account switched fromUserId={} toAccountId={}", currentUserId, targetAccountId);
        return tokens(target, false);
    }

    @Transactional
    public AuthResponse createAccount(UUID currentUserId) {
        User current = users.findById(currentUserId).orElseThrow(() -> AppException.notFound("User not found"));
        User created = new User();
        created.setPhone(current.getPhone());
        created.setVerified(true);
        created.setOnboardingStatus(OnboardingStatus.REGISTERED);
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
        log.info("account deactivated userId={}", userId);
    }

    @Transactional
    public void deleteAccount(UUID userId) {
        User user = users.findById(userId).orElseThrow(() -> AppException.notFound("User not found"));
        cache.delete("session:" + userId);
        users.delete(user);
        log.warn("account hard-deleted userId={}", userId);
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
        if (dto.getName() != null) {
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
                user.getName(),
                user.getShopName(),
                user.getEmail(),
                user.getBusinessType(),
                user.getOnboardingStatus(),
                user.getStatus(),
                loc == null ? null : loc.getAddress(),
                loc == null ? null : loc.getArea(),
                loc == null ? null : loc.getCity(),
                loc == null ? null : loc.getState(),
                loc == null ? null : loc.getPincode(),
                loc == null ? null : loc.getGeoLat(),
                loc == null ? null : loc.getGeoLng(),
                user.getVehicleCategories()
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
