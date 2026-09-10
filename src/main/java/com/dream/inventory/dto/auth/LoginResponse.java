package com.dream.inventory.dto.auth;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class LoginResponse {

    private String token;
    private UserProfileVO user;
    private List<String> permissions;
}
