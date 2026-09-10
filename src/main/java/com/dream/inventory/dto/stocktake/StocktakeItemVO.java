package com.dream.inventory.dto.stocktake;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StocktakeItemVO {

    private Long id;
    private Long skuId;
    private String skuCode;
    private Long locationId;
    private Integer snapshotQty;
    private Integer countedQty;
    private Integer diffQty;
    private String diffReason;
    private Integer excluded;
}
