package com.autoparts.inventory.repository;

import com.autoparts.inventory.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsByPhone(String phone);

    Optional<User> findByPhone(String phone);
}
