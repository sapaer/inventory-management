package com.autoparts.inventory.service;

import com.autoparts.inventory.api.AppException;
import com.autoparts.inventory.config.AppProperties;
import com.autoparts.inventory.enums.OnboardingStatus;
import com.autoparts.inventory.enums.VehicleCategory;
import com.autoparts.inventory.dto.ProfileUpdateRequest;
import com.autoparts.inventory.entity.User;
import com.autoparts.inventory.entity.UserLocation;
import com.autoparts.inventory.entity.UserVehicleCategory;
import com.autoparts.inventory.repository.UserLocationRepository;
import com.autoparts.inventory.repository.UserRepository;
import com.autoparts.inventory.repository.UserVehicleCategoryRepository;
import com.autoparts.inventory.security.JwtService;
import com.autoparts.inventory.store.RedisCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final UserVehicleCategoryRepository vehicleCategories;
    private final RedisCache cache;
    private final JwtService jwt;
    private final OtpDispatcher otp;
    private final AppProperties props;

    public AuthService(
            UserRepository users,
            UserLocationRepository locations,
            UserVehicleCategoryRepository vehicleCategories,
            RedisCache cache,
            JwtService jwt,
            OtpDispatcher otp,
            AppProperties props
    ) {
        this.users = users;
        this.locations = locations;
        this.vehicleCategories = vehicleCategories;
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
        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        try {
            otp.sendOtp(phone, code);
        } catch (Exception ex) {
            log.error("otp delivery failed phone={}", phone, ex);
            String detail = ex.getMessage() == null ? "Please try again." : ex.getMessage();
            throw AppException.badRequest("OTP_DELIVERY_FAILED", "Could not send OTP. " + detail);
        }
        cache.set("otp:" + phone, code, Duration.ofSeconds(OTP_EXPIRY_SECONDS));
        cache.incr(attemptsKey);
        cache.expire(attemptsKey, Duration.ofSeconds(ATTEMPT_WINDOW_SECONDS));
        if (props.logOtp()) {
            log.info("OTP generated for local testing phone={} otp={}", phone, code);
        } else {
            log.info("OTP sent phone={}", phone);
        }
    }

    @Transactional
    public Map<String, Object> verifyOtp(String phone, String submitted) {
        String stored = cache.get("otp:" + phone);
        if (stored == null || stored.isBlank()) {
            throw AppException.badRequest("OTP_EXPIRED", "OTP has expired. Please request a new one.");
        }
        if (!stored.equals(submitted)) {
            throw AppException.badRequest("OTP_INVALID", "Incorrect OTP. Please try again.");
        }
        cache.delete("otp:" + phone, "otp_attempts:" + phone);

        boolean isNewUser = !users.existsByPhone(phone);
        User user = users.findByPhone(phone).orElseGet(() -> {
            User created = new User();
            created.setPhone(phone);
            created.setVerified(true);
            created.setOnboardingStatus(OnboardingStatus.REGISTERED);
            return users.save(created);
        });
        if (!user.isVerified()) {
            user.setVerified(true);
            user = users.save(user);
        }
        return tokens(user, isNewUser);
    }

    public Map<String, String> refreshToken(String refreshToken) {
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
        cache.set(sessionKey, newRefresh, Duration.ofDays(props.jwt().refreshExpiryDays()));
        return Map.of("accessToken", jwt.generateAccessToken(userId, user.getPhone()), "refreshToken", newRefresh);
    }

    public void logout(UUID userId) {
        cache.delete("session:" + userId);
    }

    public Map<String, Object> getProfile(UUID userId) {
        User user = users.findById(userId).orElseThrow(() -> AppException.notFound("User not found"));
        return toUserDto(user);
    }

    @Transactional
    public Map<String, Object> updateProfile(UUID userId, ProfileUpdateRequest dto) {
        User user = users.findById(userId).orElseThrow(() -> AppException.notFound("User not found"));
        if (dto.name() != null) {
            user.setName(dto.name());
        }
        if (dto.shopName() != null) {
            user.setShopName(dto.shopName());
        }
        if (dto.email() != null) {
            user.setEmail(dto.email());
        }
        if (dto.businessType() != null) {
            user.setBusinessType(dto.businessType());
        }
        upsertLocation(userId, dto);
        if (dto.vehicleCategories() != null) {
            vehicleCategories.deleteByUserId(userId);
            for (VehicleCategory category : dto.vehicleCategories()) {
                vehicleCategories.save(new UserVehicleCategory(userId, category));
            }
        }
        if (user.getOnboardingStatus() == OnboardingStatus.REGISTERED
                && user.getName() != null && user.getShopName() != null) {
            user.setOnboardingStatus(OnboardingStatus.PROFILED);
        }
        return toUserDto(users.save(user));
    }

    private void upsertLocation(UUID userId, ProfileUpdateRequest dto) {
        if (dto.address() == null && dto.area() == null && dto.city() == null
                && dto.pincode() == null && dto.geoLat() == null && dto.geoLng() == null) {
            return;
        }
        UserLocation loc = locations.findByUserIdAndPrimaryTrue(userId).orElseGet(() -> {
            UserLocation created = new UserLocation();
            created.setUserId(userId);
            created.setPrimary(true);
            return created;
        });
        if (dto.address() != null) loc.setAddress(dto.address());
        if (dto.area() != null) loc.setArea(dto.area());
        if (dto.city() != null) loc.setCity(dto.city());
        if (dto.state() != null) loc.setState(dto.state());
        if (dto.pincode() != null) loc.setPincode(dto.pincode());
        if (dto.geoLat() != null) loc.setGeoLat(BigDecimal.valueOf(dto.geoLat()));
        if (dto.geoLng() != null) loc.setGeoLng(BigDecimal.valueOf(dto.geoLng()));
        locations.save(loc);
    }

    private Map<String, Object> tokens(User user, boolean isNewUser) {
        String access = jwt.generateAccessToken(user.getId(), user.getPhone());
        String refresh = user.getId() + ":" + UUID.randomUUID();
        cache.set("session:" + user.getId(), refresh, Duration.ofDays(props.jwt().refreshExpiryDays()));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("accessToken", access);
        out.put("refreshToken", refresh);
        out.put("user", toUserDto(user));
        out.put("isNewUser", isNewUser);
        return out;
    }

    private static Map<String, Object> toUserDto(User user) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", user.getId());
        dto.put("phone", user.getPhone());
        dto.put("name", user.getName());
        dto.put("shopName", user.getShopName());
        dto.put("onboardingStatus", user.getOnboardingStatus());
        return dto;
    }
}
