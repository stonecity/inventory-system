package com.dream.inventory.dto.stocktake;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StocktakeImportItemRequest {

    @NotBlank
    private String skuCode;

    private Long locationId;

    @NotNull
    @Min(0)
    private Integer countedQty;

    private String reason;
}
