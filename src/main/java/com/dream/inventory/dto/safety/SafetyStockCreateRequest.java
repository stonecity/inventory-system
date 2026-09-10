package com.dream.inventory.dto.safety;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SafetyStockCreateRequest {

    @NotNull
    private Long skuId;

    private Long warehouseId;

    @NotNull
    @Min(0)
    private Integer minQty;

    private Integer maxQty;
    private Integer enabled;
}
