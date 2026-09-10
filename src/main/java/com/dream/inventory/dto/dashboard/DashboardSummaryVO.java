package com.dream.inventory.dto.dashboard;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardSummaryVO {

    private long skuCount;
    private java.math.BigDecimal inventoryAmount;
    private long pendingApprovalCount;
    private long openAlertCount;
}
