package com.dream.inventory.dto.partner;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PartnerCreateRequest {

    @NotBlank
    private String code;

    @NotBlank
    private String name;

    private String contactPerson;
    private String phone;
    private String address;
    private String remark;
}
