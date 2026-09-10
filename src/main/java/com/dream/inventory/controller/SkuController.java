package com.dream.inventory.controller;

import com.dream.inventory.common.PageResult;
import com.dream.inventory.common.Result;
import com.dream.inventory.dto.product.StatusRequest;
import com.dream.inventory.dto.sku.SkuCreateRequest;
import com.dream.inventory.dto.sku.SkuUpdateRequest;
import com.dream.inventory.dto.sku.SkuVO;
import com.dream.inventory.service.SkuService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SkuController {

    private final SkuService skuService;

    @GetMapping("/skus")
    @PreAuthorize("isAuthenticated()")
    public Result<PageResult<SkuVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long spuId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(skuService.list(keyword, spuId, status, page, size));
    }

    @GetMapping("/skus/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<SkuVO> getById(@PathVariable Long id) {
        return Result.ok(skuService.getById(id));
    }

    @GetMapping("/products/{spuId}/skus")
    @PreAuthorize("isAuthenticated()")
    public Result<List<SkuVO>> listBySpu(@PathVariable Long spuId) {
        return Result.ok(skuService.listBySpuId(spuId));
    }

    @PostMapping("/products/{spuId}/skus")
    @PreAuthorize("hasAuthority('sku:write')")
    public Result<SkuVO> create(@PathVariable Long spuId, @Valid @RequestBody SkuCreateRequest request) {
        return Result.ok(skuService.create(spuId, request));
    }

    @PutMapping("/skus/{id}")
    @PreAuthorize("hasAuthority('sku:write')")
    public Result<SkuVO> update(@PathVariable Long id, @Valid @RequestBody SkuUpdateRequest request) {
        return Result.ok(skuService.update(id, request));
    }

    @PutMapping("/skus/{id}/status")
    @PreAuthorize("hasAnyAuthority('sku:disable','sku:write')")
    public Result<SkuVO> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return Result.ok(skuService.updateStatus(id, request.getStatus()));
    }
}
