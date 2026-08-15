package com.autoparts.inventory.repository;

import com.autoparts.inventory.entity.UserVehicleCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserVehicleCategoryRepository extends JpaRepository<UserVehicleCategory, UserVehicleCategory.IdKey> {
    void deleteByUserId(UUID userId);
}
