package com.dream.inventory.entity;

import com.dream.inventory.entity.enums.AlertStatus;
import com.dream.inventory.entity.enums.AlertType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "stock_alert")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_id")
    private Long ruleId;

    @Column(name = "sku_id", nullable = false)
    private Long skuId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 10)
    private AlertType alertType;

    @Column(name = "current_qty", nullable = false)
    private Integer currentQty;

    @Column(nullable = false)
    private Integer threshold;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private AlertStatus status = AlertStatus.OPEN;

    @Column(name = "handled_by")
    private Long handledBy;

    @Column(name = "handled_at")
    private Instant handledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) status = AlertStatus.OPEN;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
