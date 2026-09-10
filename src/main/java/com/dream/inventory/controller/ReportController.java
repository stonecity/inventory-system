package com.dream.inventory.controller;

import com.dream.inventory.common.Result;
import com.dream.inventory.dto.report.MovementSummaryVO;
import com.dream.inventory.dto.report.StockAgeVO;
import com.dream.inventory.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/movement-summary")
    @PreAuthorize("hasAuthority('inventory:read')")
    public Result<List<MovementSummaryVO>> movementSummary(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String groupBy) {
        return Result.ok(reportService.movementSummary(from, to, warehouseId, groupBy));
    }

    @GetMapping("/stock-age")
    @PreAuthorize("hasAuthority('inventory:read')")
    public Result<List<StockAgeVO>> stockAge(@RequestParam(required = false) Long warehouseId) {
        return Result.ok(reportService.stockAge(warehouseId));
    }
}
