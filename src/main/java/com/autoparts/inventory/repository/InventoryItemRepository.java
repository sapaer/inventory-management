package com.autoparts.inventory.repository;

import com.autoparts.inventory.entity.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {
    List<InventoryItem> findByUserIdAndActiveTrueOrderByUpdatedAtDesc(UUID userId);

    boolean existsByUserIdAndPartNameIgnoreCaseAndActiveTrue(UUID userId, String partName);

    List<InventoryItem> findByUserIdAndActiveTrueAndQuantityLessThanEqual(UUID userId, int ignored);

    @Query("select i from InventoryItem i where i.active = true and i.quantity <= i.minQuantity")
    List<InventoryItem> findAllLowStockActive();

    @Query("select i from InventoryItem i where i.userId = :userId and i.active = true and i.quantity <= i.minQuantity")
    List<InventoryItem> findLowStockByUser(@Param("userId") UUID userId);

    @Query(value = """
            SELECT id, user_id, part_name, local_name, specification, description, vehicle_category,
                   brand, model, quantity, min_quantity, selling_price, cost_price, images, is_active,
                   created_at, updated_at
            FROM inventory_items
            WHERE user_id = :userId
              AND search_vector @@ plainto_tsquery('english', :q)
              AND is_active = true
            ORDER BY ts_rank(search_vector, plainto_tsquery('english', :q)) DESC
            """, nativeQuery = true)
    List<InventoryItem> fullTextSearch(@Param("userId") UUID userId, @Param("q") String q);
}
