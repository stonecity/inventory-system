package com.dream.inventory.dto.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProductCreateRequest {

    @NotBlank(message = "商品名称不能为空")
    @Size(max = 100, message = "商品名称最多 100 字符")
    private String name;

    private Long categoryId;

    @Size(max = 50, message = "品牌最多 50 字符")
    private String brand;

    @Size(max = 10, message = "单位最多 10 字符")
    private String unit;

    @Size(max = 255, message = "备注最多 255 字符")
    private String remark;
}
