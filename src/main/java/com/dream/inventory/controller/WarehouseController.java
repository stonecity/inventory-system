package com.dream.inventory.controller;

import com.dream.inventory.common.PageResult;
import com.dream.inventory.common.Result;
import com.dream.inventory.dto.product.StatusRequest;
import com.dream.inventory.dto.warehouse.WarehouseCreateRequest;
import com.dream.inventory.dto.warehouse.WarehouseUpdateRequest;
import com.dream.inventory.dto.warehouse.WarehouseVO;
import com.dream.inventory.entity.enums.WarehouseType;
import com.dream.inventory.service.WarehouseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Result<PageResult<WarehouseVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) WarehouseType type,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(warehouseService.list(keyword, type, status, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<WarehouseVO> getById(@PathVariable Long id) {
        return Result.ok(warehouseService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:warehouse')")
    public Result<WarehouseVO> create(@Valid @RequestBody WarehouseCreateRequest request) {
        return Result.ok(warehouseService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:warehouse')")
    public Result<WarehouseVO> update(@PathVariable Long id, @Valid @RequestBody WarehouseUpdateRequest request) {
        return Result.ok(warehouseService.update(id, request));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('system:warehouse')")
    public Result<WarehouseVO> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return Result.ok(warehouseService.updateStatus(id, request.getStatus()));
    }
}
