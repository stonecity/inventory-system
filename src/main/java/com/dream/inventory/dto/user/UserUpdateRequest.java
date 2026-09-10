package com.dream.inventory.dto.user;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserUpdateRequest {

    @NotBlank
    private String realName;

    private String phone;
    private String email;
    private String password;
}
