package com.dream.inventory.dto.safety;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SafetyStockVO {

    private Long id;
    private Long skuId;
    private Long warehouseId;
    private Integer minQty;
    private Integer maxQty;
    private Integer enabled;
}
