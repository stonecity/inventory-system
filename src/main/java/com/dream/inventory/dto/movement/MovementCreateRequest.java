package com.dream.inventory.dto.movement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class MovementCreateRequest {

    @NotNull
    private Long warehouseId;

    private Long toWarehouseId;

    private Long partnerId;

    private Long refMovementId;

    private String remark;

    @NotEmpty
    @Valid
    private List<MovementItemRequest> items;
}
