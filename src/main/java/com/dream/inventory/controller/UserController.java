package com.dream.inventory.controller;

import com.dream.inventory.common.PageResult;
import com.dream.inventory.common.Result;
import com.dream.inventory.dto.product.StatusRequest;
import com.dream.inventory.dto.user.UserCreateRequest;
import com.dream.inventory.dto.user.UserRolesRequest;
import com.dream.inventory.dto.user.UserUpdateRequest;
import com.dream.inventory.dto.user.UserVO;
import com.dream.inventory.dto.user.UserWarehousesRequest;
import com.dream.inventory.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasAuthority('system:user')")
    public Result<PageResult<UserVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String roleCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(userService.list(keyword, status, roleCode, page, size));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:user')")
    public Result<UserVO> create(@Valid @RequestBody UserCreateRequest req) {
        return Result.ok(userService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user')")
    public Result<UserVO> update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest req) {
        return Result.ok(userService.update(id, req));
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('system:user')")
    public Result<UserVO> updateRoles(@PathVariable Long id, @Valid @RequestBody UserRolesRequest req) {
        return Result.ok(userService.updateRoles(id, req.getRoleCodes()));
    }

    @PutMapping("/{id}/warehouses")
    @PreAuthorize("hasAuthority('system:user')")
    public Result<UserVO> updateWarehouses(@PathVariable Long id, @Valid @RequestBody UserWarehousesRequest req) {
        return Result.ok(userService.updateWarehouses(id, req.getWarehouseIds()));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('system:user')")
    public Result<UserVO> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest req) {
        return Result.ok(userService.updateStatus(id, req.getStatus()));
    }
}
