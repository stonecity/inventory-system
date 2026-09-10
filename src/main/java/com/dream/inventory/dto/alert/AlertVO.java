package com.dream.inventory.dto.alert;

import com.dream.inventory.entity.enums.AlertStatus;
import com.dream.inventory.entity.enums.AlertType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class AlertVO {

    private Long id;
    private Long skuId;
    private String skuCode;
    private Long warehouseId;
    private AlertType alertType;
    private Integer currentQty;
    private Integer threshold;
    private AlertStatus status;
    private Instant createdAt;
}
