package com.dream.inventory.dto.stocktake;

import com.dream.inventory.entity.enums.StocktakeScope;
import com.dream.inventory.entity.enums.StocktakeStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class StocktakeVO {

    private Long id;
    private String stocktakeNo;
    private Long warehouseId;
    private StocktakeScope scope;
    private StocktakeStatus status;
    private Integer gainQty;
    private Integer lossQty;
    private java.math.BigDecimal gainAmount;
    private java.math.BigDecimal lossAmount;
    private Long adjustMovementId;
    private String remark;
    private String rejectReason;
    private Instant snapshotAt;
    private Integer version;
    private Instant createdAt;
}
