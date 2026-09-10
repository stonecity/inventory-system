package com.dream.inventory.dto.role;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PermissionVO {

    private Long id;
    private String code;
    private String name;
    private String module;
    private Long parentId;
}
