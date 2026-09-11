package com.dream.inventory.repository;

import com.dream.inventory.entity.StockMovementItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface StockMovementItemRepository extends JpaRepository<StockMovementItem, Long> {

    List<StockMovementItem> findByMovementIdOrderByIdAsc(Long movementId);

    List<StockMovementItem> findByMovementIdInOrderByIdAsc(Collection<Long> movementIds);
}
