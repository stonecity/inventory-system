package com.dream.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "stocktake_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StocktakeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stocktake_id", nullable = false)
    private Stocktake stocktake;

    @Column(name = "sku_id", nullable = false)
    private Long skuId;

    @Column(name = "location_id", nullable = false)
    @Builder.Default
    private Long locationId = 0L;

    @Column(name = "snapshot_qty", nullable = false)
    private Integer snapshotQty;

    @Column(name = "counted_qty")
    private Integer countedQty;

    @Column(name = "diff_qty")
    private Integer diffQty;

    @Column(name = "diff_reason", length = 255)
    private String diffReason;

    @Column(nullable = false)
    @Builder.Default
    private Integer excluded = 0;

    @Column(name = "counted_by")
    private Long countedBy;

    @Column(name = "counted_at")
    private Instant countedAt;

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
        if (excluded == null) excluded = 0;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
