package com.dream.inventory.dto.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CategoryUpdateRequest {

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 50, message = "分类名称最多 50 字符")
    private String name;

    private Integer sortOrder;
}
