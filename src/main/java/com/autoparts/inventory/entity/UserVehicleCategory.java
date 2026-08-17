package com.autoparts.inventory.entity;

import com.autoparts.inventory.enums.VehicleCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_vehicle_categories")
@IdClass(UserVehicleCategory.IdKey.class)
public class UserVehicleCategory {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Enumerated(EnumType.STRING)
    private VehicleCategory category;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IdKey implements Serializable {
        private UUID userId;
        private VehicleCategory category;
    }
}
