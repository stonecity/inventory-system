package com.dream.inventory.dto.movement;

import com.dream.inventory.entity.enums.ItemCondition;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ExecuteItemRequest {

    @NotNull
    private Long itemId;

    @NotNull
    @Min(0)
    private Integer actualQty;

    private Long locationId;

    private ItemCondition condition;
}
