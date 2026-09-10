package com.dream.inventory.dto.role;

import lombok.Data;

import java.util.Set;

@Data
public class RolePermissionsRequest {

    private Set<String> permissionCodes;
}
