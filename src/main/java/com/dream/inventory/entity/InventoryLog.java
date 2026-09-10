package com.dream.inventory.entity;

import com.dream.inventory.entity.enums.InventoryChangeType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "inventory_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inventory_id", nullable = false)
    private Long inventoryId;

    @Column(name = "sku_id", nullable = false)
    private Long skuId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "location_id", nullable = false)
    @Builder.Default
    private Long locationId = 0L;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 20)
    private InventoryChangeType changeType;

    @Column(name = "delta_qty", nullable = false)
    private Integer deltaQty;

    @Column(name = "on_hand_before", nullable = false)
    private Integer onHandBefore;

    @Column(name = "on_hand_after", nullable = false)
    private Integer onHandAfter;

    @Column(name = "reserved_before", nullable = false)
    private Integer reservedBefore;

    @Column(name = "reserved_after", nullable = false)
    private Integer reservedAfter;

    @Column(name = "available_before", nullable = false)
    private Integer availableBefore;

    @Column(name = "available_after", nullable = false)
    private Integer availableAfter;

    @Column(name = "movement_id")
    private Long movementId;

    @Column(name = "movement_item_id")
    private Long movementItemId;

    @Column(name = "stocktake_id")
    private Long stocktakeId;

    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    @Column(length = 255)
    private String remark;

    @Column(name = "operated_at", nullable = false)
    private Instant operatedAt;

    @PrePersist
    void prePersist() {
        if (operatedAt == null) operatedAt = Instant.now();
        if (locationId == null) locationId = 0L;
    }
}
