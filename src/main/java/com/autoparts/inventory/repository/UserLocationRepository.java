package com.autoparts.inventory.repository;

import com.autoparts.inventory.entity.UserLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserLocationRepository extends JpaRepository<UserLocation, UUID> {
    Optional<UserLocation> findByUserId(UUID userId);

    @Modifying
    @Query("delete from UserLocation l where l.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);
}
