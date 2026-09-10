package com.autoparts.inventory.dto;

import com.autoparts.inventory.enums.AccountStatus;
import com.autoparts.inventory.enums.BusinessType;
import com.autoparts.inventory.enums.OnboardingStatus;
import com.autoparts.inventory.enums.VehicleCategory;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse {
    private final UUID id;
    private final String phone;
    private final String firstName;
    private final String lastName;
    private final String name;
    private final String shopName;
    private final String email;
    private final BusinessType businessType;
    private final OnboardingStatus onboardingStatus;
    private final AccountStatus status;
    /** Set only when status is PENDING_DELETION — when the deletion was requested. */
    private final Instant deletionRequestedAt;
    private final String address;
    private final String area;
    private final String city;
    private final String state;
    private final String pincode;
    private final BigDecimal geoLat;
    private final BigDecimal geoLng;
    private final List<VehicleCategory> vehicleCategories;

    /** Never the hash itself — just whether one is set, so the UI can offer "set" vs "change". */
    private final boolean hasPassword;

    /** Whether WhatsApp low-stock alerts are on for this account. */
    private final boolean whatsappAlertsEnabled;

    private final String photoUrl;
    private final String shopPhotoUrl;
    private final String gstin;
    private final String altPhone;
}
