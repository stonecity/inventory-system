package com.dream.inventory.repository;

import com.dream.inventory.entity.SafetyStockRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SafetyStockRuleRepository extends JpaRepository<SafetyStockRule, Long> {

    Optional<SafetyStockRule> findBySkuIdAndWarehouseId(Long skuId, Long warehouseId);

    List<SafetyStockRule> findBySkuId(Long skuId);

    List<SafetyStockRule> findByEnabled(Integer enabled);
}
