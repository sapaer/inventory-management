package com.autoparts.inventory.entity;

import com.autoparts.inventory.enums.ChangeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "inventory_history")
public class InventoryHistory {
    @Id
    private UUID id;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type")
    private ChangeType changeType;

    @Column(name = "qty_before", nullable = false)
    private int qtyBefore;

    @Column(name = "qty_change", nullable = false)
    private int qtyChange;

    @Column(name = "qty_after", nullable = false)
    private int qtyAfter;

    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }
}
