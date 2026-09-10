package com.autoparts.inventory.repository;

import com.autoparts.inventory.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long deleteByCreatedAtBefore(Instant cutoff);

    @Modifying
    @Query("delete from Notification n where n.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);
}
