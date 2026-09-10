package com.dream.inventory.controller;

import com.dream.inventory.common.Result;
import com.dream.inventory.dto.dashboard.DashboardSummaryVO;
import com.dream.inventory.dto.dashboard.TopSkuVO;
import com.dream.inventory.dto.dashboard.TrendPointVO;
import com.dream.inventory.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    @PreAuthorize("isAuthenticated()")
    public Result<DashboardSummaryVO> summary() {
        return Result.ok(dashboardService.summary());
    }

    @GetMapping("/trend")
    @PreAuthorize("isAuthenticated()")
    public Result<List<TrendPointVO>> trend(@RequestParam(defaultValue = "30") int days) {
        return Result.ok(dashboardService.trend(days));
    }

    @GetMapping("/top-skus")
    @PreAuthorize("isAuthenticated()")
    public Result<List<TopSkuVO>> topSkus(@RequestParam(defaultValue = "10") int limit) {
        return Result.ok(dashboardService.topSkus(limit));
    }
}
