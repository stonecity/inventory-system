package com.dream.inventory.controller;

import com.dream.inventory.common.PageResult;
import com.dream.inventory.common.Result;
import com.dream.inventory.entity.SysAuditLog;
import com.dream.inventory.service.AuditLogService;
import com.dream.inventory.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SystemController {

    private final AuditLogService auditLogService;
    private final SettingsService settingsService;

    @GetMapping("/audit-logs")
    @PreAuthorize("hasAuthority('system:audit')")
    public Result<PageResult<SysAuditLog>> auditLogs(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(auditLogService.list(userId, action, from, to, page, size));
    }

    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('system:user')")
    public Result<Map<String, String>> getSettings() {
        return Result.ok(settingsService.getAll());
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('system:user')")
    public Result<Map<String, String>> updateSettings(@RequestBody Map<String, String> settings) {
        return Result.ok(settingsService.update(settings));
    }
}
