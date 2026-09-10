package com.dream.inventory.repository;

import com.dream.inventory.entity.StockAlert;
import com.dream.inventory.entity.enums.AlertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StockAlertRepository extends JpaRepository<StockAlert, Long> {

    Optional<StockAlert> findBySkuIdAndWarehouseIdAndStatus(Long skuId, Long warehouseId, AlertStatus status);

    long countByStatus(AlertStatus status);

    @Query("""
            SELECT a FROM StockAlert a
            WHERE (:status IS NULL OR a.status = :status)
              AND (:warehouseId IS NULL OR a.warehouseId = :warehouseId)
            """)
    Page<StockAlert> search(@Param("status") AlertStatus status,
                            @Param("warehouseId") Long warehouseId,
                            Pageable pageable);
}
