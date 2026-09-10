package com.dream.inventory.dto.sku;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Data
@Builder
public class SkuVO {

    private Long id;
    private Long spuId;
    private String spuName;
    private String skuCode;
    private String barcode;
    private Map<String, String> specJson;
    private BigDecimal costPrice;
    private BigDecimal salePrice;
    private Integer defaultSafetyStock;
    private Integer status;
    private Integer version;
    private Instant createdAt;
    private Instant updatedAt;
}
