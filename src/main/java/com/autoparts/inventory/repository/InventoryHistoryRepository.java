package com.autoparts.inventory.repository;

import com.autoparts.inventory.entity.InventoryHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InventoryHistoryRepository extends JpaRepository<InventoryHistory, UUID> {
    Page<InventoryHistory> findByItemIdOrderByCreatedAtDesc(UUID itemId, Pageable pageable);
}
