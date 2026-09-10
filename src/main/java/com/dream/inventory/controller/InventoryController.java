package com.dream.inventory.controller;

import com.dream.inventory.common.PageResult;
import com.dream.inventory.common.Result;
import com.dream.inventory.dto.inventory.InventoryLogVO;
import com.dream.inventory.dto.inventory.InventoryVO;
import com.dream.inventory.entity.enums.InventoryChangeType;
import com.dream.inventory.service.InventoryQueryService;
import com.dream.inventory.service.StockMovementService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryQueryService inventoryQueryService;
    private final StockMovementService movementService;

    @GetMapping
    @PreAuthorize("hasAuthority('inventory:read')")
    public Result<PageResult<InventoryVO>> list(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long skuId,
            @RequestParam(defaultValue = "false") boolean lowStockOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(inventoryQueryService.list(warehouseId, skuId, lowStockOnly, page, size));
    }

    @GetMapping("/sku/{skuId}")
    @PreAuthorize("hasAuthority('inventory:read')")
    public Result<List<InventoryVO>> bySku(@PathVariable Long skuId) {
        return Result.ok(inventoryQueryService.getBySku(skuId));
    }

    @GetMapping("/logs")
    @PreAuthorize("hasAuthority('inventory:log')")
    public Result<PageResult<InventoryLogVO>> logs(
            @RequestParam(required = false) Long skuId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) InventoryChangeType changeType,
            @RequestParam(required = false) Long movementId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(inventoryQueryService.listLogs(skuId, warehouseId, changeType, movementId, from, to, page, size));
    }

    @GetMapping("/available")
    @PreAuthorize("hasAuthority('inventory:read')")
    public Result<Map<Long, Integer>> available(
            @RequestParam Long warehouseId,
            @RequestParam List<Long> skuIds) {
        return Result.ok(movementService.getAvailable(warehouseId, skuIds));
    }
}
