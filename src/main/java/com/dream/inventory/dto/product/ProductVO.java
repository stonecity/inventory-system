package com.dream.inventory.dto.product;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ProductVO {

    private Long id;
    private String name;
    private Long categoryId;
    private String brand;
    private String unit;
    private Integer status;
    private String remark;
    private Instant createdAt;
    private Instant updatedAt;
}
