package com.dream.inventory.dto.location;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LocationUpdateRequest {

    @Size(max = 20, message = "区域最多 20 字符")
    private String zone;

    @Size(max = 20, message = "货架最多 20 字符")
    private String shelf;

    private Integer isDefault;

    private Integer status;
}
