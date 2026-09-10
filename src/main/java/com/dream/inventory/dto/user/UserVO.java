package com.dream.inventory.dto.user;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Set;

@Data
@Builder
public class UserVO {

    private Long id;
    private String username;
    private String realName;
    private String phone;
    private String email;
    private Integer status;
    private Instant lastLoginAt;
    private Set<String> roleCodes;
    private Set<Long> warehouseIds;
}
