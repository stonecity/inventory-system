package com.dream.inventory.dto.safety;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class SafetyStockVO {

    private Long id;
    private Long skuId;
    private String skuCode;
    private String spuName;
    private Map<String, String> specJson;
    private Long warehouseId;
    private String warehouseName;
    private Integer minQty;
    private Integer maxQty;
    private Integer enabled;
}
