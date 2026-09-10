package com.dream.inventory.controller;

import com.dream.inventory.common.Result;
import com.dream.inventory.dto.dashboard.DashboardSummaryVO;
import com.dream.inventory.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
