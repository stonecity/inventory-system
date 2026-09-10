package com.dream.inventory.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAgeVO {

    private Long skuId;
    private String skuCode;
    private Long warehouseId;
    private String warehouseName;
    private int onHandQty;
    private Instant lastInboundAt;
    private long ageDays;
}
