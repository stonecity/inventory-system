package com.dream.inventory.service;

import com.dream.inventory.common.PageResult;
import com.dream.inventory.common.TraceContext;
import com.dream.inventory.entity.SysAuditLog;
import com.dream.inventory.repository.SysAuditLogRepository;
import com.dream.inventory.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final SysAuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void log(String action, String resourceType, String resourceId, Map<String, Object> detail) {
        var user = SecurityUtils.currentUser();
        auditLogRepository.save(SysAuditLog.builder()
                .userId(user != null ? user.getId() : null)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .detail(detail)
                .traceId(TraceContext.getOrCreate())
                .build());
    }

    public PageResult<SysAuditLog> list(Long userId, String action, Instant from, Instant to, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<SysAuditLog> result = auditLogRepository.search(userId, action, from, to, pageable);
        return PageResult.of(result.getContent(), result.getTotalElements(), page, size);
    }
}
