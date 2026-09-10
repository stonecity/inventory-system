package com.dream.inventory.dto.warehouse;

import com.dream.inventory.entity.enums.WarehouseType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class WarehouseVO {

    private Long id;
    private String code;
    private String name;
    private WarehouseType type;
    private String address;
    private Long managerUserId;
    private Integer status;
    private Integer locationCount;
    private Instant createdAt;
    private Instant updatedAt;
}
