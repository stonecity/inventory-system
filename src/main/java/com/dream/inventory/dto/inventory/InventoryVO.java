package com.dream.inventory.dto.inventory;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class InventoryVO {

    private Long id;
    private Long skuId;
    private String skuCode;
    private Long warehouseId;
    private String warehouseName;
    private Long locationId;
    private Integer onHandQty;
    private Integer reservedQty;
    private Integer availableQty;
    private Integer inTransitQty;
    private Integer locked;
    private Long lockStocktakeId;
    private Integer version;
    private Instant updatedAt;
}
