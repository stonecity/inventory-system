package com.dream.inventory.repository;

import com.dream.inventory.entity.StockAlert;
import com.dream.inventory.entity.enums.AlertStatus;
import com.dream.inventory.entity.enums.AlertType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StockAlertRepository extends JpaRepository<StockAlert, Long> {

    Optional<StockAlert> findBySkuIdAndWarehouseIdAndStatus(Long skuId, Long warehouseId, AlertStatus status);

    long countByStatus(AlertStatus status);

    List<StockAlert> findByIdIn(Collection<Long> ids);

    @Query("""
            SELECT a FROM StockAlert a
            WHERE (:status IS NULL OR a.status = :status)
              AND (:warehouseId IS NULL OR a.warehouseId = :warehouseId)
              AND (:alertType IS NULL OR a.alertType = :alertType)
            """)
    Page<StockAlert> search(@Param("status") AlertStatus status,
                            @Param("warehouseId") Long warehouseId,
                            @Param("alertType") AlertType alertType,
                            Pageable pageable);
}
