package com.dream.inventory.service;

import com.dream.inventory.common.BizException;
import com.dream.inventory.common.ErrorCode;
import com.dream.inventory.dto.auth.LoginRequest;
import com.dream.inventory.dto.auth.LoginResponse;
import com.dream.inventory.dto.auth.UserProfileVO;
import com.dream.inventory.entity.SysUser;
import com.dream.inventory.repository.SysUserRepository;
import com.dream.inventory.security.JwtTokenProvider;
import com.dream.inventory.security.LoginUser;
import com.dream.inventory.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final SysUserRepository userRepository;

    @Transactional(rollbackFor = Exception.class)
    public LoginResponse login(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
            LoginUser loginUser = (LoginUser) authentication.getPrincipal();

            SysUser user = userRepository.findById(loginUser.getId())
                    .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "用户不存在"));
            user.setLastLoginAt(Instant.now());
            userRepository.save(user);

            String token = jwtTokenProvider.createToken(loginUser.getId(), loginUser.getUsername());
            return LoginResponse.builder()
                    .token(token)
                    .user(toProfile(loginUser))
                    .permissions(new ArrayList<>(loginUser.getPermissionCodes()))
                    .build();
        } catch (BadCredentialsException ex) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
    }

    public UserProfileVO me() {
        LoginUser user = SecurityUtils.currentUser();
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return toProfile(user);
    }

    public List<String> myPermissions() {
        LoginUser user = SecurityUtils.currentUser();
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return new ArrayList<>(user.getPermissionCodes());
    }

    private UserProfileVO toProfile(LoginUser user) {
        return UserProfileVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .roles(new ArrayList<>(user.getRoleCodes()))
                .warehouseIds(new ArrayList<>(user.getWarehouseIds()))
                .build();
    }
}
