package com.dream.inventory.dto.role;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Set;

@Data
public class RoleCreateRequest {

    @NotBlank
    private String code;

    @NotBlank
    private String name;

    private String description;

    private Set<String> permissionCodes;
}
