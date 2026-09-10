package com.dream.inventory.service;

import com.dream.inventory.dto.dashboard.DashboardSummaryVO;
import com.dream.inventory.entity.enums.MovementStatus;
import com.dream.inventory.repository.ProductSkuRepository;
import com.dream.inventory.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ProductSkuRepository skuRepository;
    private final StockMovementRepository movementRepository;
    private final AlertService alertService;

    public DashboardSummaryVO summary() {
        return DashboardSummaryVO.builder()
                .skuCount(skuRepository.count())
                .pendingApprovalCount(movementRepository.countByStatus(MovementStatus.PENDING_APPROVAL)
                        + movementRepository.countByStatus(MovementStatus.RESERVED))
                .openAlertCount(alertService.countOpen())
                .build();
    }
}
