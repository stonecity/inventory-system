package com.dream.inventory.dto.user;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Set;

@Data
public class UserCreateRequest {

    @NotBlank
    private String username;

    @NotBlank
    private String password;

    @NotBlank
    private String realName;

    private String phone;
    private String email;
    private Set<String> roleCodes;
    private Set<Long> warehouseIds;
}
