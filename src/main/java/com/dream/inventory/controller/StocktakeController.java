package com.dream.inventory.controller;

import com.dream.inventory.common.PageResult;
import com.dream.inventory.common.Result;
import com.dream.inventory.dto.movement.VersionRequest;
import com.dream.inventory.dto.stocktake.*;
import com.dream.inventory.entity.enums.StocktakeStatus;
import com.dream.inventory.service.StocktakeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stocktakes")
@RequiredArgsConstructor
public class StocktakeController {

    private final StocktakeService stocktakeService;

    @GetMapping
    @PreAuthorize("hasAuthority('stocktake:create') or hasAuthority('stocktake:approve')")
    public Result<PageResult<StocktakeVO>> list(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) StocktakeStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(stocktakeService.list(warehouseId, status, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('stocktake:create','stocktake:count','stocktake:approve')")
    public Result<StocktakeVO> getById(@PathVariable Long id) {
        return Result.ok(stocktakeService.getById(id));
    }

    @GetMapping("/{id}/items")
    @PreAuthorize("hasAnyAuthority('stocktake:count','stocktake:approve')")
    public Result<List<StocktakeItemVO>> getItems(@PathVariable Long id) {
        return Result.ok(stocktakeService.getItems(id));
    }

    @PostMapping("/precheck")
    @PreAuthorize("hasAuthority('stocktake:create')")
    public Result<List<String>> precheck(@Valid @RequestBody StocktakeCreateRequest req) {
        return Result.ok(stocktakeService.precheck(req));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('stocktake:create')")
    public Result<StocktakeVO> create(@Valid @RequestBody StocktakeCreateRequest req) {
        return Result.ok(stocktakeService.create(req));
    }

    @PostMapping("/{id}/lock")
    @PreAuthorize("hasAuthority('stocktake:create')")
    public Result<StocktakeVO> lock(@PathVariable Long id) {
        return Result.ok(stocktakeService.lock(id));
    }

    @PutMapping("/{id}/items/{itemId}")
    @PreAuthorize("hasAuthority('stocktake:count')")
    public Result<StocktakeItemVO> updateItem(@PathVariable Long id, @PathVariable Long itemId,
                                            @Valid @RequestBody StocktakeCountRequest req) {
        return Result.ok(stocktakeService.updateItem(id, itemId, req));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('stocktake:count')")
    public Result<StocktakeVO> submit(@PathVariable Long id) {
        return Result.ok(stocktakeService.submit(id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('stocktake:approve')")
    public Result<StocktakeVO> approve(@PathVariable Long id, @RequestBody(required = false) VersionRequest req) {
        Integer version = req == null ? null : req.getVersion();
        return Result.ok(stocktakeService.approve(id, version));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('stocktake:approve')")
    public Result<StocktakeVO> reject(@PathVariable Long id, @Valid @RequestBody VersionRequest req) {
        return Result.ok(stocktakeService.reject(id, req.getReason()));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('stocktake:create')")
    public Result<StocktakeVO> cancel(@PathVariable Long id) {
        return Result.ok(stocktakeService.cancel(id));
    }

    @PostMapping("/{id}/items/import")
    @PreAuthorize("hasAuthority('stocktake:count')")
    public Result<Integer> importItems(@PathVariable Long id,
                                       @Valid @RequestBody List<StocktakeImportItemRequest> rows) {
        return Result.ok(stocktakeService.importItems(id, rows));
    }

    @GetMapping("/{id}/report")
    @PreAuthorize("hasAnyAuthority('stocktake:create','stocktake:count','stocktake:approve')")
    public ResponseEntity<byte[]> report(@PathVariable Long id) {
        byte[] csv = stocktakeService.report(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=stocktake-report.csv")
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(csv);
    }
}
