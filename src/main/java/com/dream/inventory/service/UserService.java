package com.dream.inventory.service;

import com.dream.inventory.common.BizException;
import com.dream.inventory.common.ErrorCode;
import com.dream.inventory.common.PageResult;
import com.dream.inventory.dto.user.UserCreateRequest;
import com.dream.inventory.dto.user.UserUpdateRequest;
import com.dream.inventory.dto.user.UserVO;
import com.dream.inventory.entity.SysRole;
import com.dream.inventory.entity.SysUser;
import com.dream.inventory.repository.SysRoleRepository;
import com.dream.inventory.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public PageResult<UserVO> list(int page, int size) {
        Page<SysUser> result = userRepository.findAll(PageRequest.of(page, size, Sort.by("id")));
        return PageResult.of(result.getContent().stream().map(this::toVO).toList(),
                result.getTotalElements(), page, size);
    }

    @Transactional(rollbackFor = Exception.class)
    public UserVO create(UserCreateRequest req) {
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "用户名已存在");
        }
        Set<SysRole> roles = resolveRoles(req.getRoleCodes());
        SysUser user = SysUser.builder()
                .username(req.getUsername())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .realName(req.getRealName())
                .phone(req.getPhone())
                .email(req.getEmail())
                .status(1)
                .roles(roles)
                .warehouseIds(req.getWarehouseIds() != null ? new HashSet<>(req.getWarehouseIds()) : new HashSet<>())
                .build();
        return toVO(userRepository.save(user));
    }

    @Transactional(rollbackFor = Exception.class)
    public UserVO update(Long id, UserUpdateRequest req) {
        SysUser user = findOrThrow(id);
        user.setRealName(req.getRealName());
        user.setPhone(req.getPhone());
        user.setEmail(req.getEmail());
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        }
        return toVO(userRepository.save(user));
    }

    @Transactional(rollbackFor = Exception.class)
    public UserVO updateRoles(Long id, Set<String> roleCodes) {
        SysUser user = findOrThrow(id);
        user.setRoles(resolveRoles(roleCodes));
        return toVO(userRepository.save(user));
    }

    @Transactional(rollbackFor = Exception.class)
    public UserVO updateWarehouses(Long id, Set<Long> warehouseIds) {
        SysUser user = findOrThrow(id);
        user.setWarehouseIds(warehouseIds != null ? new HashSet<>(warehouseIds) : new HashSet<>());
        return toVO(userRepository.save(user));
    }

    @Transactional(rollbackFor = Exception.class)
    public UserVO updateStatus(Long id, Integer status) {
        SysUser user = findOrThrow(id);
        user.setStatus(status);
        return toVO(userRepository.save(user));
    }

    private Set<SysRole> resolveRoles(Set<String> codes) {
        if (codes == null || codes.isEmpty()) return new HashSet<>();
        return codes.stream()
                .map(code -> roleRepository.findByCode(code)
                        .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "角色不存在: " + code)))
                .collect(Collectors.toSet());
    }

    private SysUser findOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "用户不存在"));
    }

    private UserVO toVO(SysUser user) {
        return UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .phone(user.getPhone())
                .email(user.getEmail())
                .status(user.getStatus())
                .roleCodes(user.getRoles().stream().map(SysRole::getCode).collect(Collectors.toSet()))
                .warehouseIds(user.getWarehouseIds())
                .build();
    }
}
