package com.dream.inventory.repository;

import com.dream.inventory.entity.InventoryLog;
import com.dream.inventory.entity.enums.InventoryChangeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface InventoryLogRepository extends JpaRepository<InventoryLog, Long> {

    List<InventoryLog> findByMovementIdOrderByOperatedAtAsc(Long movementId);

    @Query("""
            SELECT l FROM InventoryLog l
            WHERE (:skuId IS NULL OR l.skuId = :skuId)
              AND (:warehouseId IS NULL OR l.warehouseId = :warehouseId)
              AND (:changeType IS NULL OR l.changeType = :changeType)
              AND (:movementId IS NULL OR l.movementId = :movementId)
              AND (:movementNo IS NULL OR EXISTS (
                    SELECT 1 FROM StockMovement m WHERE m.id = l.movementId AND m.movementNo = :movementNo))
              AND (:from IS NULL OR l.operatedAt >= :from)
              AND (:to IS NULL OR l.operatedAt <= :to)
            """)
    Page<InventoryLog> search(@Param("skuId") Long skuId,
                              @Param("warehouseId") Long warehouseId,
                              @Param("changeType") InventoryChangeType changeType,
                              @Param("movementId") Long movementId,
                              @Param("movementNo") String movementNo,
                              @Param("from") Instant from,
                              @Param("to") Instant to,
                              Pageable pageable);

    @Query(value = """
            SELECT DATE(operated_at) AS d, change_type, SUM(ABS(delta_qty))
              FROM inventory_log
             WHERE operated_at >= :from
             GROUP BY DATE(operated_at), change_type
            """, nativeQuery = true)
    List<Object[]> trendSince(@Param("from") Instant from);

    @Query("""
            SELECT l FROM InventoryLog l
             WHERE l.skuId = :skuId AND l.warehouseId = :warehouseId
               AND l.changeType IN :inboundTypes
             ORDER BY l.operatedAt DESC
            """)
    List<InventoryLog> findLatestInbound(@Param("skuId") Long skuId,
                                         @Param("warehouseId") Long warehouseId,
                                         @Param("inboundTypes") List<InventoryChangeType> inboundTypes,
                                         Pageable pageable);
}
