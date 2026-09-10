package com.dream.inventory.dto.partner;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class PartnerVO {

    private Long id;
    private String code;
    private String name;
    private String contactPerson;
    private String phone;
    private String address;
    private Integer status;
    private String remark;
    private Instant createdAt;
}
