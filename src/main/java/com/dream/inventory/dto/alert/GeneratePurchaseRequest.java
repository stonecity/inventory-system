package com.dream.inventory.dto.alert;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class GeneratePurchaseRequest {

    @NotEmpty
    private List<Long> alertIds;

    @NotNull
    private Long supplierId;
}
