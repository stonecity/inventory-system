package com.dream.inventory.service;

import com.dream.inventory.common.BizException;
import com.dream.inventory.common.ErrorCode;
import com.dream.inventory.dto.role.PermissionVO;
import com.dream.inventory.dto.role.RoleCreateRequest;
import com.dream.inventory.dto.role.RoleVO;
import com.dream.inventory.entity.SysPermission;
import com.dream.inventory.entity.SysRole;
import com.dream.inventory.repository.SysPermissionRepository;
import com.dream.inventory.repository.SysRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final SysRoleRepository roleRepository;
    private final SysPermissionRepository permissionRepository;

    public List<RoleVO> listRoles() {
        return roleRepository.findAll().stream().map(this::toVO).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public RoleVO create(RoleCreateRequest req) {
        if (roleRepository.findByCode(req.getCode()).isPresent()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "角色编码已存在");
        }
        SysRole role = SysRole.builder()
                .code(req.getCode().trim())
                .name(req.getName().trim())
                .description(req.getDescription())
                .build();
        if (req.getPermissionCodes() != null && !req.getPermissionCodes().isEmpty()) {
            role.setPermissions(req.getPermissionCodes().stream()
                    .map(code -> permissionRepository.findByCode(code)
                            .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "权限不存在: " + code)))
                    .collect(Collectors.toSet()));
        }
        return toVO(roleRepository.save(role));
    }

    public List<PermissionVO> listPermissions() {
        return permissionRepository.findAll().stream()
                .map(p -> PermissionVO.builder()
                        .id(p.getId()).code(p.getCode()).name(p.getName())
                        .module(p.getModule()).parentId(p.getParentId()).build())
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public RoleVO updatePermissions(Long roleId, Set<String> permissionCodes) {
        SysRole role = roleRepository.findById(roleId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "角色不存在"));
        Set<SysPermission> perms = permissionCodes.stream()
                .map(code -> permissionRepository.findByCode(code)
                        .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "权限不存在: " + code)))
                .collect(Collectors.toSet());
        role.setPermissions(perms);
        return toVO(roleRepository.save(role));
    }

    private RoleVO toVO(SysRole role) {
        return RoleVO.builder()
                .id(role.getId())
                .code(role.getCode())
                .name(role.getName())
                .description(role.getDescription())
                .permissionCodes(role.getPermissions().stream().map(SysPermission::getCode).collect(Collectors.toSet()))
                .build();
    }
}
