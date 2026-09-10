package com.dream.inventory.dto.inventory;

import com.dream.inventory.entity.enums.InventoryChangeType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class InventoryLogVO {

    private Long id;
    private Long skuId;
    private String skuCode;
    private Long warehouseId;
    private String warehouseName;
    private Long locationId;
    private InventoryChangeType changeType;
    private Integer deltaQty;
    private Integer onHandBefore;
    private Integer onHandAfter;
    private Integer availableBefore;
    private Integer availableAfter;
    private Long movementId;
    private String movementNo;
    private Long operatorId;
    private String traceId;
    private String remark;
    private Instant operatedAt;
}
