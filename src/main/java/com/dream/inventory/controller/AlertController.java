package com.dream.inventory.controller;

import com.dream.inventory.common.PageResult;
import com.dream.inventory.common.Result;
import com.dream.inventory.dto.alert.AlertVO;
import com.dream.inventory.dto.alert.GeneratePurchaseRequest;
import com.dream.inventory.dto.movement.MovementVO;
import com.dream.inventory.entity.enums.AlertStatus;
import com.dream.inventory.entity.enums.AlertType;
import com.dream.inventory.service.AlertService;
import com.dream.inventory.service.StockMovementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;
    private final StockMovementService movementService;

    @GetMapping
    @PreAuthorize("hasAuthority('alert:read')")
    public Result<PageResult<AlertVO>> list(
            @RequestParam(required = false) AlertStatus status,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) AlertType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(alertService.list(status, warehouseId, type, page, size));
    }

    @PostMapping("/{id}/ack")
    @PreAuthorize("hasAuthority('alert:handle')")
    public Result<AlertVO> ack(@PathVariable Long id) {
        return Result.ok(alertService.ack(id));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('alert:handle')")
    public Result<AlertVO> close(@PathVariable Long id) {
        return Result.ok(alertService.close(id));
    }

    @PostMapping("/scan")
    @PreAuthorize("hasAuthority('alert:handle')")
    public Result<Void> scan() {
        alertService.scanAll();
        return Result.ok();
    }

    @PostMapping("/generate-purchase")
    @PreAuthorize("hasAuthority('purchase:create')")
    public Result<MovementVO> generatePurchase(@Valid @RequestBody GeneratePurchaseRequest req) {
        return Result.ok(movementService.createPurchaseFromAlerts(req.getAlertIds(), req.getSupplierId()));
    }
}
