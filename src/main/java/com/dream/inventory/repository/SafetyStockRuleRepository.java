package com.dream.inventory.repository;

import com.dream.inventory.entity.SafetyStockRule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SafetyStockRuleRepository extends JpaRepository<SafetyStockRule, Long> {

    Optional<SafetyStockRule> findBySkuIdAndWarehouseId(Long skuId, Long warehouseId);

    List<SafetyStockRule> findBySkuId(Long skuId);

    List<SafetyStockRule> findByEnabled(Integer enabled);

    @Query("""
            SELECT r FROM SafetyStockRule r
            WHERE (:warehouseId IS NULL OR r.warehouseId = :warehouseId)
              AND (:enabled IS NULL OR r.enabled = :enabled)
              AND (:filterBySku = false OR r.skuId IN :skuIds)
            """)
    Page<SafetyStockRule> search(@Param("warehouseId") Long warehouseId,
                                 @Param("enabled") Integer enabled,
                                 @Param("filterBySku") boolean filterBySku,
                                 @Param("skuIds") Collection<Long> skuIds,
                                 Pageable pageable);
}
