package com.dream.inventory.dto.stocktake;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StocktakeCountRequest {

    @NotNull
    private Integer countedQty;

    private String reason;
}
