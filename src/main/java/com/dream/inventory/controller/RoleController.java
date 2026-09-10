package com.dream.inventory.controller;

import com.dream.inventory.common.Result;
import com.dream.inventory.dto.role.PermissionVO;
import com.dream.inventory.dto.role.RoleCreateRequest;
import com.dream.inventory.dto.role.RolePermissionsRequest;
import com.dream.inventory.dto.role.RoleVO;
import com.dream.inventory.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping("/roles")
    @PreAuthorize("hasAnyAuthority('system:role', 'system:user')")
    public Result<List<RoleVO>> listRoles() {
        return Result.ok(roleService.listRoles());
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('system:role')")
    public Result<RoleVO> create(@Valid @RequestBody RoleCreateRequest req) {
        return Result.ok(roleService.create(req));
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('system:role')")
    public Result<List<PermissionVO>> listPermissions() {
        return Result.ok(roleService.listPermissions());
    }

    @PutMapping("/roles/{id}/permissions")
    @PreAuthorize("hasAuthority('system:role')")
    public Result<RoleVO> updatePermissions(@PathVariable Long id, @Valid @RequestBody RolePermissionsRequest req) {
        return Result.ok(roleService.updatePermissions(id, req.getPermissionCodes()));
    }
}
