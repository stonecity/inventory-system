package com.dream.inventory.dto.partner;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PartnerUpdateRequest {

    @NotBlank
    private String name;

    private String contactPerson;
    private String phone;
    private String address;
    private String remark;
}
