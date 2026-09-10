package com.dream.inventory.dto.role;

import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Data
@Builder
public class RoleVO {

    private Long id;
    private String code;
    private String name;
    private String description;
    private Set<String> permissionCodes;
}
