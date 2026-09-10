package com.dream.inventory.controller;

import com.dream.inventory.common.Result;
import com.dream.inventory.dto.auth.LoginRequest;
import com.dream.inventory.dto.auth.LoginResponse;
import com.dream.inventory.dto.auth.UserProfileVO;
import com.dream.inventory.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> logout() {
        return Result.ok();
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public Result<UserProfileVO> me() {
        return Result.ok(authService.me());
    }
}
