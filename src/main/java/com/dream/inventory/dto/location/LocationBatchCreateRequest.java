package com.dream.inventory.dto.location;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LocationBatchCreateRequest {

    @NotBlank(message = "区域前缀不能为空")
    @Size(max = 20, message = "区域前缀最多 20 字符")
    private String zone;

    @Min(value = 1, message = "排数至少为 1")
    @Max(value = 99, message = "排数最多 99")
    private int rows = 1;

    @Min(value = 1, message = "层数至少为 1")
    @Max(value = 99, message = "层数最多 99")
    private int layers = 1;
}
