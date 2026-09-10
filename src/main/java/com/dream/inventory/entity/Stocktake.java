package com.dream.inventory.entity;

import com.dream.inventory.entity.enums.StocktakeScope;
import com.dream.inventory.entity.enums.StocktakeStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "stocktake")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Stocktake {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stocktake_no", nullable = false, unique = true, length = 32)
    private String stocktakeNo;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StocktakeScope scope;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scope_value", columnDefinition = "json")
    private List<Long> scopeValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StocktakeStatus status = StocktakeStatus.CREATED;

    @Column(name = "snapshot_at")
    private Instant snapshotAt;

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Column(name = "approver_id")
    private Long approverId;

    @Column(name = "adjust_movement_id")
    private Long adjustMovementId;

    @Column(name = "gain_qty", nullable = false)
    @Builder.Default
    private Integer gainQty = 0;

    @Column(name = "loss_qty", nullable = false)
    @Builder.Default
    private Integer lossQty = 0;

    @Column(name = "gain_amount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal gainAmount = BigDecimal.ZERO;

    @Column(name = "loss_amount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal lossAmount = BigDecimal.ZERO;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "reject_reason", length = 255)
    private String rejectReason;

    @Column(length = 255)
    private String remark;

    @Version
    @Column(nullable = false)
    private Integer version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "stocktake", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<StocktakeItem> items = new ArrayList<>();

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) status = StocktakeStatus.CREATED;
        if (gainQty == null) gainQty = 0;
        if (lossQty == null) lossQty = 0;
        if (gainAmount == null) gainAmount = BigDecimal.ZERO;
        if (lossAmount == null) lossAmount = BigDecimal.ZERO;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
