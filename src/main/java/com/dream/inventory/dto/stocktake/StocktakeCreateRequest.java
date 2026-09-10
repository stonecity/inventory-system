package com.dream.inventory.dto.stocktake;

import com.dream.inventory.entity.enums.StocktakeScope;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class StocktakeCreateRequest {

    @NotNull
    private Long warehouseId;

    @NotNull
    private StocktakeScope scope;

    private List<Long> scopeValue;

    private String remark;
}
