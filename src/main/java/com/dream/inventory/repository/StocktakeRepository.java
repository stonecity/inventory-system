package com.dream.inventory.repository;

import com.dream.inventory.entity.Stocktake;
import com.dream.inventory.entity.enums.StocktakeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface StocktakeRepository extends JpaRepository<Stocktake, Long> {

    @Query("""
            SELECT s FROM Stocktake s
            WHERE (:warehouseId IS NULL OR s.warehouseId = :warehouseId)
              AND (:status IS NULL OR s.status = :status)
            """)
    Page<Stocktake> search(@Param("warehouseId") Long warehouseId,
                           @Param("status") StocktakeStatus status,
                           Pageable pageable);

    List<Stocktake> findByWarehouseIdAndStatusIn(Long warehouseId, Collection<StocktakeStatus> statuses);

    boolean existsByWarehouseIdAndStatusIn(Long warehouseId, Collection<StocktakeStatus> statuses);

    List<Stocktake> findByStatusInAndSnapshotAtBefore(Collection<StocktakeStatus> statuses, java.time.Instant before);

    List<Stocktake> findByStatusInAndCreatedAtBefore(Collection<StocktakeStatus> statuses, java.time.Instant before);
}
