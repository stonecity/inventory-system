package com.dream.inventory.repository;

import com.dream.inventory.entity.StockMovement;
import com.dream.inventory.entity.enums.MovementStatus;
import com.dream.inventory.entity.enums.MovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    @Query("SELECT m FROM StockMovement m LEFT JOIN FETCH m.items WHERE m.id = :id")
    java.util.Optional<StockMovement> findWithItemsById(@Param("id") Long id);

    @Query("""
            SELECT m FROM StockMovement m
            WHERE (:type IS NULL OR m.type = :type)
              AND (:status IS NULL OR m.status = :status)
              AND (:warehouseId IS NULL OR m.warehouseId = :warehouseId)
              AND (:partnerId IS NULL OR m.partnerId = :partnerId)
              AND (:from IS NULL OR m.createdAt >= :from)
              AND (:to IS NULL OR m.createdAt <= :to)
            """)
    Page<StockMovement> search(@Param("type") MovementType type,
                               @Param("status") MovementStatus status,
                               @Param("warehouseId") Long warehouseId,
                               @Param("partnerId") Long partnerId,
                               @Param("from") Instant from,
                               @Param("to") Instant to,
                               Pageable pageable);

    List<StockMovement> findByWarehouseIdAndStatusIn(Long warehouseId, Collection<MovementStatus> statuses);

    List<StockMovement> findByStatusAndTypeAndSubmittedAtBefore(
            MovementStatus status, MovementType type, Instant before);

    @Modifying
    @Query("""
            UPDATE StockMovement m SET m.status = :newStatus, m.version = m.version + 1
            WHERE m.id = :id AND m.status = :expectedStatus AND m.version = :version
            """)
    int updateStatus(@Param("id") Long id,
                     @Param("expectedStatus") MovementStatus expectedStatus,
                     @Param("newStatus") MovementStatus newStatus,
                     @Param("version") Integer version);

    long countByStatus(MovementStatus status);

    @Query("""
            SELECT m.type, m.warehouseId, COUNT(m), COALESCE(SUM(m.totalQty), 0), COALESCE(SUM(m.totalAmount), 0)
              FROM StockMovement m
             WHERE m.status IN :statuses
               AND (:from IS NULL OR m.createdAt >= :from)
               AND (:to IS NULL OR m.createdAt <= :to)
               AND (:warehouseId IS NULL OR m.warehouseId = :warehouseId)
             GROUP BY m.type, m.warehouseId
            """)
    List<Object[]> summarize(@Param("from") Instant from,
                             @Param("to") Instant to,
                             @Param("warehouseId") Long warehouseId,
                             @Param("statuses") Collection<MovementStatus> statuses);
}
