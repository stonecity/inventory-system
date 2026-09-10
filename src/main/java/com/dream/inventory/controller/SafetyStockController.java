package com.dream.inventory.controller;

import com.dream.inventory.common.PageResult;
import com.dream.inventory.common.Result;
import com.dream.inventory.dto.safety.SafetyStockCreateRequest;
import com.dream.inventory.dto.safety.SafetyStockVO;
import com.dream.inventory.service.SafetyStockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/safety-stock-rules")
@RequiredArgsConstructor
public class SafetyStockController {

    private final SafetyStockService safetyStockService;

    @GetMapping
    @PreAuthorize("hasAuthority('safety-stock:write') or hasAuthority('alert:read')")
    public Result<PageResult<SafetyStockVO>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(safetyStockService.list(page, size));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('safety-stock:write')")
    public Result<SafetyStockVO> create(@Valid @RequestBody SafetyStockCreateRequest req) {
        return Result.ok(safetyStockService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('safety-stock:write')")
    public Result<SafetyStockVO> update(@PathVariable Long id, @Valid @RequestBody SafetyStockCreateRequest req) {
        return Result.ok(safetyStockService.update(id, req));
    }

    @PostMapping("/import")
    @PreAuthorize("hasAuthority('safety-stock:write')")
    public Result<Integer> importRules(@Valid @RequestBody java.util.List<SafetyStockCreateRequest> items) {
        return Result.ok(safetyStockService.importRules(items));
    }
}
