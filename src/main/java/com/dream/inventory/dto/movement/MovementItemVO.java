package com.dream.inventory.dto.movement;

import com.dream.inventory.entity.enums.ItemCondition;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class MovementItemVO {

    private Long id;
    private Long skuId;
    private String skuCode;
    private Long locationId;
    private Integer plannedQty;
    private Integer actualQty;
    private Integer returnedQty;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private ItemCondition cond;
    private String remark;
}
