package com.dream.inventory.dto.user;

import lombok.Data;

import java.util.Set;

@Data
public class UserWarehousesRequest {

    private Set<Long> warehouseIds;
}
