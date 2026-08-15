package com.autoparts.inventory.entity;

import com.autoparts.inventory.enums.BusinessType;
import com.autoparts.inventory.enums.OnboardingStatus;
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

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 10)
    private String phone;

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
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
