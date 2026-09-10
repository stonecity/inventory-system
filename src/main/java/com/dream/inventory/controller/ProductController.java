package com.dream.inventory.controller;

import com.dream.inventory.common.PageResult;
import com.dream.inventory.common.Result;
import com.dream.inventory.dto.product.ProductCreateRequest;
import com.dream.inventory.dto.product.ProductUpdateRequest;
import com.dream.inventory.dto.product.ProductVO;
import com.dream.inventory.dto.product.StatusRequest;
import com.dream.inventory.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Result<PageResult<ProductVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(productService.list(keyword, status, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<ProductVO> getById(@PathVariable Long id) {
        return Result.ok(productService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('sku:write')")
    public Result<ProductVO> create(@Valid @RequestBody ProductCreateRequest request) {
        return Result.ok(productService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sku:write')")
    public Result<ProductVO> update(@PathVariable Long id, @Valid @RequestBody ProductUpdateRequest request) {
        return Result.ok(productService.update(id, request));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('sku:disable')")
    public Result<ProductVO> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return Result.ok(productService.updateStatus(id, request.getStatus()));
    }
}
