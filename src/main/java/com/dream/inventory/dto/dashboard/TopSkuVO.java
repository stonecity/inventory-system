package com.dream.inventory.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopSkuVO {

    private Long skuId;
    private String skuCode;
    private String skuName;
    private int onHandQty;
    private BigDecimal amount;
}
