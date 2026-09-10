package com.dream.inventory.dto.movement;

import com.dream.inventory.entity.enums.MovementStatus;
import com.dream.inventory.entity.enums.MovementType;
import com.dream.inventory.entity.enums.PartnerType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class MovementVO {

    private Long id;
    private String movementNo;
    private MovementType type;
    private MovementStatus status;
    private Long warehouseId;
    private Long toWarehouseId;
    private PartnerType partnerType;
    private Long partnerId;
    private Long refMovementId;
    private Integer totalQty;
    private BigDecimal totalAmount;
    private Long creatorId;
    private Long approverId;
    private Long executorId;
    private Instant submittedAt;
    private Instant approvedAt;
    private Instant executedAt;
    private Instant cancelledAt;
    private String cancelReason;
    private String rejectReason;
    private String remark;
    private Integer version;
    private Instant createdAt;
    private List<MovementItemVO> items;
}
