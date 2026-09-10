package com.dream.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "inventory")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sku_id", nullable = false)
    private Long skuId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "location_id", nullable = false)
    @Builder.Default
    private Long locationId = 0L;

    @Column(name = "on_hand_qty", nullable = false)
    @Builder.Default
    private Integer onHandQty = 0;

    @Column(name = "reserved_qty", nullable = false)
    @Builder.Default
    private Integer reservedQty = 0;

    @Column(name = "available_qty", nullable = false)
    @Builder.Default
    private Integer availableQty = 0;

    @Column(name = "in_transit_qty", nullable = false)
    @Builder.Default
    private Integer inTransitQty = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer locked = 0;

    @Column(name = "lock_stocktake_id")
    private Long lockStocktakeId;

    @Version
    @Column(nullable = false)
    private Integer version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (locationId == null) locationId = 0L;
        if (onHandQty == null) onHandQty = 0;
        if (reservedQty == null) reservedQty = 0;
        if (availableQty == null) availableQty = 0;
        if (inTransitQty == null) inTransitQty = 0;
        if (locked == null) locked = 0;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
