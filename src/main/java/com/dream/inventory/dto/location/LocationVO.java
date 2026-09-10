package com.dream.inventory.dto.location;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class LocationVO {

    private Long id;
    private Long warehouseId;
    private String code;
    private String zone;
    private String shelf;
    private Integer isDefault;
    private Integer status;
    private Instant createdAt;
    private Instant updatedAt;
}
