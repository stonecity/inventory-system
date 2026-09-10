package com.dream.inventory.dto.movement;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class PrintMovementVO {

    private MovementVO movement;
    private String warehouseName;
    private String toWarehouseName;
    private String partnerName;
    private Instant printedAt;
    private List<MovementItemVO> items;
}
