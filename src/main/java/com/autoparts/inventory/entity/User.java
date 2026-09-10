package com.autoparts.inventory.entity;

import com.autoparts.inventory.enums.AccountStatus;
import com.autoparts.inventory.enums.BusinessType;
import com.autoparts.inventory.enums.OnboardingStatus;
import com.autoparts.inventory.enums.VehicleCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User {
    @Id
    private UUID id;

    @Column(nullable = false, length = 10)
    private String phone;

    @Column(name = "first_name", length = 50)
    private String firstName;

    @Column(name = "last_name", length = 50)
    private String lastName;

    /** Legacy/display name, kept in sync with first + last name. */
    private String name;

    @Column(name = "shop_name")
    private String shopName;

    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_type")
    private BusinessType businessType;

    @Enumerated(EnumType.STRING)
    @Column(name = "onboarding_status", nullable = false)
    private OnboardingStatus onboardingStatus = OnboardingStatus.REGISTERED;

    @Column(name = "is_verified", nullable = false)
    private boolean verified;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

    /** When DELETE /account was called. Row is purged 30 days after this unless the user returns. */
    @Column(name = "deletion_requested_at")
    private Instant deletionRequestedAt;

    /** Null means this account has never set a password and can only log in via OTP. */
    @Column(name = "password_hash")
    private String passwordHash;

    /** When false, the low-stock scheduler skips the WhatsApp message for this account. */
    @Column(name = "whatsapp_alerts_enabled", nullable = false)
    private boolean whatsappAlertsEnabled = true;

    /** Public URL of an uploaded profile photo. */
    @Column(name = "photo_url")
    private String photoUrl;

    /** Public URL of an uploaded shop / storefront photo. */
    @Column(name = "shop_photo_url")
    private String shopPhotoUrl;

    /** Shop's GST registration number (shop-specific, optional). */
    @Column(name = "gstin", length = 20)
    private String gstin;

    /** Optional secondary contact number (landline / alternate mobile). */
    @Column(name = "alt_phone", length = 15)
    private String altPhone;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "vehicle_categories", nullable = false, columnDefinition = "json")
    private List<VehicleCategory> vehicleCategories = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (vehicleCategories == null) {
            vehicleCategories = new ArrayList<>();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Set first/last name and refresh the legacy {@link #name} field from them. */
    public void applyName(String firstName, String lastName) {
        if (firstName != null) {
            this.firstName = firstName.isBlank() ? null : firstName.trim();
        }
        if (lastName != null) {
            this.lastName = lastName.isBlank() ? null : lastName.trim();
        }
        String combined = ((this.firstName == null ? "" : this.firstName) + " "
                + (this.lastName == null ? "" : this.lastName)).trim();
        this.name = combined.isEmpty() ? null : combined;
    }
}
