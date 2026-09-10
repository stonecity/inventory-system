package com.dream.inventory.dto.warehouse;

import com.dream.inventory.entity.enums.WarehouseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WarehouseUpdateRequest {

    @NotBlank(message = "仓库名称不能为空")
    @Size(max = 50, message = "仓库名称最多 50 字符")
    private String name;

    @NotNull(message = "仓库类型不能为空")
    private WarehouseType type;

    @Size(max = 200, message = "地址最多 200 字符")
    private String address;

    private Long managerUserId;
}
