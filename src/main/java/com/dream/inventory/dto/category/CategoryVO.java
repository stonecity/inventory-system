package com.dream.inventory.dto.category;

import lombok.Builder;
import lombok.Data;

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
    private List<CategoryVO> children = new ArrayList<>();
}
