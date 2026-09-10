package com.dream.inventory.entity;

import com.dream.inventory.entity.enums.MovementStatus;
import com.dream.inventory.entity.enums.MovementType;
import com.dream.inventory.entity.enums.PartnerType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "stock_movement")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "movement_no", nullable = false, unique = true, length = 32)
    private String movementNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MovementType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private MovementStatus status = MovementStatus.DRAFT;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "to_warehouse_id")
    private Long toWarehouseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "partner_type", length = 20)
    private PartnerType partnerType;

    @Column(name = "partner_id")
    private Long partnerId;

    @Column(name = "ref_movement_id")
    private Long refMovementId;

    @Column(name = "ref_stocktake_id")
    private Long refStocktakeId;

    @Column(name = "total_qty", nullable = false)
    @Builder.Default
    private Integer totalQty = 0;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Column(name = "approver_id")
    private Long approverId;

    @Column(name = "executor_id")
    private Long executorId;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

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

    @OneToMany(mappedBy = "movement", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<StockMovementItem> items = new ArrayList<>();

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) status = MovementStatus.DRAFT;
        if (totalQty == null) totalQty = 0;
        if (totalAmount == null) totalAmount = BigDecimal.ZERO;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public void addItem(StockMovementItem item) {
        items.add(item);
        item.setMovement(this);
    }
}
