package com.dream.inventory.dto.auth;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class UserProfileVO {

    private Long id;
    private String username;
    private String realName;
    private List<String> roles;
    private List<Long> warehouseIds;
}
