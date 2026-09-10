package com.dream.inventory.dto.movement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ExecuteRequest {

    @NotNull
    private Integer version;

    @NotEmpty
    @Valid
    private List<ExecuteItemRequest> items;
}
