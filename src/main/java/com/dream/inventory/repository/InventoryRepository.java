package com.dream.inventory.repository;

import com.dream.inventory.entity.Inventory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findBySkuIdAndWarehouseIdAndLocationId(Long skuId, Long warehouseId, Long locationId);

    List<Inventory> findBySkuIdOrderByWarehouseIdAsc(Long skuId);

    List<Inventory> findByWarehouseIdAndSkuIdIn(Long warehouseId, Collection<Long> skuIds);

    List<Inventory> findByLockStocktakeId(Long stocktakeId);

    @Query("""
            SELECT i FROM Inventory i
            WHERE (:warehouseId IS NULL OR i.warehouseId = :warehouseId)
              AND (:skuId IS NULL OR i.skuId = :skuId)
              AND (:lowStockOnly = false OR i.availableQty <= :lowStockThreshold)
            """)
    Page<Inventory> search(@Param("warehouseId") Long warehouseId,
                           @Param("skuId") Long skuId,
                           @Param("lowStockOnly") boolean lowStockOnly,
                           @Param("lowStockThreshold") int lowStockThreshold,
                           Pageable pageable);

    @Modifying
    @Query("""
            UPDATE Inventory i SET i.reservedQty = i.reservedQty + :q,
                i.availableQty = i.availableQty - :q, i.version = i.version + 1
            WHERE i.id = :id AND i.version = :version AND i.availableQty >= :q AND i.locked = 0
            """)
    int reserve(@Param("id") Long id, @Param("version") Integer version, @Param("q") int q);

    @Modifying
    @Query("""
            UPDATE Inventory i SET i.reservedQty = i.reservedQty - :q,
                i.availableQty = i.availableQty + :q, i.version = i.version + 1
            WHERE i.id = :id AND i.version = :version AND i.reservedQty >= :q
            """)
    int releaseReserve(@Param("id") Long id, @Param("version") Integer version, @Param("q") int q);

    @Modifying
    @Query("""
            UPDATE Inventory i SET i.onHandQty = i.onHandQty - :q,
                i.reservedQty = i.reservedQty - :q, i.version = i.version + 1
            WHERE i.id = :id AND i.version = :version AND i.reservedQty >= :q AND i.onHandQty >= :q AND i.locked = 0
            """)
    int deduct(@Param("id") Long id, @Param("version") Integer version, @Param("q") int q);

    @Modifying
    @Query("""
            UPDATE Inventory i SET i.onHandQty = i.onHandQty + :q,
                i.availableQty = i.availableQty + :q, i.version = i.version + 1
            WHERE i.id = :id AND i.version = :version AND i.locked = 0
            """)
    int increase(@Param("id") Long id, @Param("version") Integer version, @Param("q") int q);

    @Modifying
    @Query("""
            UPDATE Inventory i SET i.onHandQty = i.onHandQty - :q,
                i.availableQty = i.availableQty - :q, i.version = i.version + 1
            WHERE i.id = :id AND i.version = :version AND i.availableQty >= :q AND i.locked = 0
            """)
    int decreaseAvailable(@Param("id") Long id, @Param("version") Integer version, @Param("q") int q);

    @Modifying
    @Query("""
            UPDATE Inventory i SET i.onHandQty = i.onHandQty + :q,
                i.availableQty = i.availableQty + :q, i.version = i.version + 1
            WHERE i.id = :id AND i.version = :version AND i.locked = 0
            """)
    int adjustGain(@Param("id") Long id, @Param("version") Integer version, @Param("q") int q);

    @Modifying
    @Query("""
            UPDATE Inventory i SET i.onHandQty = i.onHandQty - :q,
                i.availableQty = i.availableQty - :q, i.version = i.version + 1
            WHERE i.id = :id AND i.version = :version AND i.onHandQty >= :q AND i.locked = 0
            """)
    int adjustLoss(@Param("id") Long id, @Param("version") Integer version, @Param("q") int q);

    @Modifying
    @Query("""
            UPDATE Inventory i SET i.inTransitQty = i.inTransitQty + :q, i.version = i.version + 1
            WHERE i.id = :id AND i.version = :version
            """)
    int addInTransit(@Param("id") Long id, @Param("version") Integer version, @Param("q") int q);

    @Modifying
    @Query("""
            UPDATE Inventory i SET i.inTransitQty = i.inTransitQty - :q,
                i.onHandQty = i.onHandQty + :q, i.availableQty = i.availableQty + :q,
                i.version = i.version + 1
            WHERE i.id = :id AND i.version = :version AND i.inTransitQty >= :q
            """)
    int receiveInTransit(@Param("id") Long id, @Param("version") Integer version, @Param("q") int q);

    @Modifying
    @Query("""
            UPDATE Inventory i SET i.inTransitQty = i.inTransitQty - :q, i.version = i.version + 1
            WHERE i.id = :id AND i.version = :version AND i.inTransitQty >= :q
            """)
    int cancelInTransit(@Param("id") Long id, @Param("version") Integer version, @Param("q") int q);

    @Query("SELECT COALESCE(SUM(i.onHandQty), 0) FROM Inventory i WHERE i.warehouseId = :warehouseId")
    long sumOnHandByWarehouse(@Param("warehouseId") Long warehouseId);
}
