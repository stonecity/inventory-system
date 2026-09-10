package com.dream.inventory.dto.sku;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class SkuUpdateRequest {

    @NotBlank(message = "SKU 编码不能为空")
    @Size(max = 50, message = "SKU 编码最多 50 字符")
    private String skuCode;

    @Size(max = 64, message = "条码最多 64 字符")
    private String barcode;

    private Map<String, String> specJson;

    @DecimalMin(value = "0", message = "成本价不能为负")
    private BigDecimal costPrice;

    @DecimalMin(value = "0", message = "售价不能为负")
    private BigDecimal salePrice;

    @Min(value = 0, message = "安全库存不能为负")
    private Integer defaultSafetyStock;

    @NotNull(message = "版本号不能为空")
    private Integer version;
}
