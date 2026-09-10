package com.dream.inventory.dto.movement;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VersionRequest {

    @NotNull
    private Integer version;

    private String reason;
}
