package com.autoparts.inventory.repository;

import com.autoparts.inventory.entity.UserLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserLocationRepository extends JpaRepository<UserLocation, UUID> {
    Optional<UserLocation> findByUserId(UUID userId);
}
