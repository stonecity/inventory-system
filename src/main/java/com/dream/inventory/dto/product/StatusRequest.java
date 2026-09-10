package com.dream.inventory.dto.product;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StatusRequest {

    @NotNull(message = "状态不能为空")
    private Integer status;
}
