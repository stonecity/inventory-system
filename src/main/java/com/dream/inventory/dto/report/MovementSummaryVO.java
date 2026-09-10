package com.dream.inventory.dto.report;

import com.dream.inventory.entity.enums.MovementType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovementSummaryVO {

    private MovementType type;
    private Long warehouseId;
    private String warehouseName;
    private String groupKey;
    private long orderCount;
    private int totalQty;
    private BigDecimal totalAmount;
}
