package com.dream.inventory.controller;

import com.dream.inventory.common.Result;
import com.dream.inventory.dto.category.CategoryCreateRequest;
import com.dream.inventory.dto.category.CategoryUpdateRequest;
import com.dream.inventory.dto.category.CategoryVO;
import com.dream.inventory.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Result<List<CategoryVO>> tree() {
        return Result.ok(categoryService.tree());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('sku:write')")
    public Result<CategoryVO> create(@Valid @RequestBody CategoryCreateRequest request) {
        return Result.ok(categoryService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sku:write')")
    public Result<CategoryVO> update(@PathVariable Long id, @Valid @RequestBody CategoryUpdateRequest request) {
        return Result.ok(categoryService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('sku:write')")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.ok();
    }
}
