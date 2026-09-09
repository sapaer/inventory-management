package com.autoparts.inventory.repository;

import com.autoparts.inventory.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    List<User> findAllByPhone(String phone);

    List<User> findByDeletionRequestedAtBefore(Instant cutoff);
}
