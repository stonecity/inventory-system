package com.dream.inventory.controller;

import com.dream.inventory.common.PageResult;
import com.dream.inventory.common.Result;
import com.dream.inventory.dto.movement.*;
import com.dream.inventory.entity.enums.MovementStatus;
import com.dream.inventory.entity.enums.MovementType;
import com.dream.inventory.service.StockMovementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/movements")
@RequiredArgsConstructor
public class MovementController {

    private final StockMovementService movementService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Result<PageResult<MovementVO>> list(
            @RequestParam(required = false) MovementType type,
            @RequestParam(required = false) MovementStatus status,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(movementService.list(type, status, warehouseId, partnerId, from, to, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<MovementVO> getById(@PathVariable Long id) {
        return Result.ok(movementService.getById(id));
    }

    @GetMapping("/{id}/logs")
    @PreAuthorize("hasAuthority('inventory:log')")
    public Result<List<com.dream.inventory.dto.inventory.InventoryLogVO>> getLogs(@PathVariable Long id) {
        return Result.ok(movementService.getLogs(id));
    }

    @GetMapping("/{id}/print")
    @PreAuthorize("isAuthenticated()")
    public Result<PrintMovementVO> print(@PathVariable Long id) {
        return Result.ok(movementService.print(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<MovementVO> update(@PathVariable Long id, @Valid @RequestBody MovementCreateRequest req) {
        return Result.ok(movementService.update(id, req));
    }

    @PostMapping("/purchase-in")
    @PreAuthorize("hasAuthority('purchase:create')")
    public Result<MovementVO> createPurchaseIn(@Valid @RequestBody MovementCreateRequest req) {
        return Result.ok(movementService.createPurchaseIn(req));
    }

    @PostMapping("/sale-out")
    @PreAuthorize("hasAuthority('sale:create')")
    public Result<MovementVO> createSaleOut(@Valid @RequestBody MovementCreateRequest req) {
        return Result.ok(movementService.createSaleOut(req));
    }

    @PostMapping("/transfer")
    @PreAuthorize("hasAuthority('transfer:create')")
    public Result<MovementVO> createTransfer(@Valid @RequestBody MovementCreateRequest req) {
        return Result.ok(movementService.createTransfer(req));
    }

    @PostMapping("/sale-return")
    @PreAuthorize("hasAuthority('return:create')")
    public Result<MovementVO> createSaleReturn(@Valid @RequestBody MovementCreateRequest req) {
        return Result.ok(movementService.createSaleReturn(req));
    }

    @PostMapping("/purchase-return")
    @PreAuthorize("hasAuthority('return:create')")
    public Result<MovementVO> createPurchaseReturn(@Valid @RequestBody MovementCreateRequest req) {
        return Result.ok(movementService.createPurchaseReturn(req));
    }

    @PostMapping("/other-in")
    @PreAuthorize("hasAuthority('inventory:adjust')")
    public Result<MovementVO> createOtherIn(@Valid @RequestBody MovementCreateRequest req) {
        return Result.ok(movementService.createOtherIn(req));
    }

    @PostMapping("/other-out")
    @PreAuthorize("hasAuthority('inventory:adjust')")
    public Result<MovementVO> createOtherOut(@Valid @RequestBody MovementCreateRequest req) {
        return Result.ok(movementService.createOtherOut(req));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("isAuthenticated()")
    public Result<MovementVO> submit(@PathVariable Long id, @Valid @RequestBody VersionRequest req) {
        return Result.ok(movementService.submit(id, req));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyAuthority('purchase:approve','sale:approve','transfer:approve')")
    public Result<MovementVO> approve(@PathVariable Long id, @Valid @RequestBody VersionRequest req) {
        return Result.ok(movementService.approve(id, req));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyAuthority('purchase:approve','sale:approve','transfer:approve')")
    public Result<MovementVO> reject(@PathVariable Long id, @Valid @RequestBody VersionRequest req) {
        return Result.ok(movementService.reject(id, req));
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAnyAuthority('purchase:receive','return:execute','inventory:adjust')")
    public Result<MovementVO> receive(@PathVariable Long id, @Valid @RequestBody ExecuteRequest req) {
        return Result.ok(movementService.receive(id, req));
    }

    @PostMapping("/{id}/pick")
    @PreAuthorize("hasAuthority('sale:ship')")
    public Result<MovementVO> pick(@PathVariable Long id, @Valid @RequestBody VersionRequest req) {
        return Result.ok(movementService.pick(id, req));
    }

    @PostMapping("/{id}/ship")
    @PreAuthorize("hasAnyAuthority('sale:ship','return:execute','inventory:adjust')")
    public Result<MovementVO> ship(@PathVariable Long id, @Valid @RequestBody ExecuteRequest req) {
        return Result.ok(movementService.ship(id, req));
    }

    @PostMapping("/{id}/transfer-out")
    @PreAuthorize("hasAuthority('transfer:execute')")
    public Result<MovementVO> transferOut(@PathVariable Long id, @Valid @RequestBody ExecuteRequest req) {
        return Result.ok(movementService.transferOut(id, req));
    }

    @PostMapping("/{id}/transfer-in")
    @PreAuthorize("hasAuthority('transfer:execute')")
    public Result<MovementVO> transferIn(@PathVariable Long id, @Valid @RequestBody ExecuteRequest req) {
        return Result.ok(movementService.transferIn(id, req));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('sale:cancel','purchase:create','transfer:create')")
    public Result<MovementVO> cancel(@PathVariable Long id, @Valid @RequestBody VersionRequest req) {
        return Result.ok(movementService.cancel(id, req));
    }
}
