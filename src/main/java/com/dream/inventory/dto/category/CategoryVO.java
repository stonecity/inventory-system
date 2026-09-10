package com.dream.inventory.dto.category;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class CategoryVO {

    private Long id;
    private String name;
    private Long parentId;
    private Integer sortOrder;
    @Builder.Default
    private Integer productCount = 0;
    @Builder.Default
    private Integer totalProductCount = 0;
    @Builder.Default
    private Integer childCount = 0;
    private Instant createdAt;
    private Instant updatedAt;
    @Builder.Default
    private List<CategoryVO> children = new ArrayList<>();
}
