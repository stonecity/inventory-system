package com.dream.inventory.entity;

import com.dream.inventory.entity.enums.ItemCondition;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "stock_movement_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovementItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movement_id", nullable = false)
    private StockMovement movement;

    @Column(name = "sku_id", nullable = false)
    private Long skuId;

    @Column(name = "location_id")
    private Long locationId;

    @Column(name = "planned_qty", nullable = false)
    private Integer plannedQty;

    @Column(name = "actual_qty")
    private Integer actualQty;

    @Column(name = "returned_qty", nullable = false)
    @Builder.Default
    private Integer returnedQty = 0;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal amount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ItemCondition cond;

    @Column(name = "batch_no", length = 50)
    private String batchNo;

    @Column(name = "expire_date")
    private LocalDate expireDate;

    @Column(length = 255)
    private String remark;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (returnedQty == null) returnedQty = 0;
        if (unitPrice == null) unitPrice = BigDecimal.ZERO;
        if (amount == null) amount = BigDecimal.ZERO;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
