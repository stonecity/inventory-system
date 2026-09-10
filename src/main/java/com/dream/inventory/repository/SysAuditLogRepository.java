package com.dream.inventory.repository;

import com.dream.inventory.entity.SysAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface SysAuditLogRepository extends JpaRepository<SysAuditLog, Long> {

    @Query("""
            SELECT a FROM SysAuditLog a
            WHERE (:userId IS NULL OR a.userId = :userId)
              AND (:action IS NULL OR a.action = :action)
              AND (:from IS NULL OR a.createdAt >= :from)
              AND (:to IS NULL OR a.createdAt <= :to)
            """)
    Page<SysAuditLog> search(@Param("userId") Long userId,
                             @Param("action") String action,
                             @Param("from") Instant from,
                             @Param("to") Instant to,
                             Pageable pageable);
}
