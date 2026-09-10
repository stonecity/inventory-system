package com.dream.inventory.dto.movement;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MovementItemRequest {

    private Long skuId;

    private Long locationId;

    @NotNull
    @Min(1)
    private Integer plannedQty;

    private BigDecimal unitPrice;

    private String remark;
}
