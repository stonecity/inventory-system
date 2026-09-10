package com.dream.inventory.controller;

import com.dream.inventory.common.Result;
import com.dream.inventory.dto.location.LocationBatchCreateRequest;
import com.dream.inventory.dto.location.LocationBatchCreateResult;
import com.dream.inventory.dto.location.LocationUpdateRequest;
import com.dream.inventory.dto.location.LocationVO;
import com.dream.inventory.service.LocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    @GetMapping("/warehouses/{warehouseId}/locations")
    @PreAuthorize("isAuthenticated()")
    public Result<List<LocationVO>> list(@PathVariable Long warehouseId) {
        return Result.ok(locationService.listByWarehouse(warehouseId));
    }

    @PostMapping("/warehouses/{warehouseId}/locations/batch")
    @PreAuthorize("hasAuthority('system:warehouse')")
    public Result<LocationBatchCreateResult> batchCreate(
            @PathVariable Long warehouseId,
            @Valid @RequestBody LocationBatchCreateRequest request) {
        return Result.ok(locationService.batchCreate(warehouseId, request));
    }

    @PutMapping("/locations/{id}")
    @PreAuthorize("hasAuthority('system:warehouse')")
    public Result<LocationVO> update(@PathVariable Long id, @Valid @RequestBody LocationUpdateRequest request) {
        return Result.ok(locationService.update(id, request));
    }
}
