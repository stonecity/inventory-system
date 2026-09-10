package com.dream.inventory.repository;

import com.dream.inventory.entity.Inventory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findBySkuIdAndWarehouseIdAndLocationId(Long skuId, Long warehouseId, Long locationId);

    List<Inventory> findBySkuIdOrderByWarehouseIdAsc(Long skuId);

    List<Inventory> findByWarehouseIdAndSkuIdIn(Long warehouseId, Collection<Long> skuIds);

    List<Inventory> findByLockStocktakeId(Long stocktakeId);

    List<Inventory> findByWarehouseId(Long warehouseId);

    @Query("""
            SELECT i FROM Inventory i
            WHERE (:warehouseId IS NULL OR i.warehouseId = :warehouseId)
              AND (:skuId IS NULL OR i.skuId = :skuId)
              AND (:lowStockOnly = false OR i.availableQty <= :lowStockThreshold)
              AND (:keyword IS NULL OR EXISTS (
                    SELECT 1 FROM ProductSku s
                     WHERE s.id = i.skuId
                       AND (s.skuCode LIKE CONCAT('%', :keyword, '%')
                            OR s.barcode LIKE CONCAT('%', :keyword, '%'))))
              AND (:categoryId IS NULL OR EXISTS (
                    SELECT 1 FROM ProductSku s, Product p
                     WHERE s.id = i.skuId AND p.id = s.spuId AND p.categoryId = :categoryId))
            """)
    Page<Inventory> search(@Param("warehouseId") Long warehouseId,
                           @Param("skuId") Long skuId,
                           @Param("lowStockOnly") boolean lowStockOnly,
                           @Param("lowStockThreshold") int lowStockThreshold,
                           @Param("keyword") String keyword,
                           @Param("categoryId") Long categoryId,
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
            WHERE i.id = :id AND i.version = :version
            """)
    int adjustGain(@Param("id") Long id, @Param("version") Integer version, @Param("q") int q);

    @Modifying
    @Query("""
            UPDATE Inventory i SET i.onHandQty = i.onHandQty - :q,
                i.availableQty = i.availableQty - :q, i.version = i.version + 1
            WHERE i.id = :id AND i.version = :version AND i.onHandQty >= :q
            """)
    int adjustLoss(@Param("id") Long id, @Param("version") Integer version, @Param("q") int q);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Inventory i SET i.locked = 1, i.lockStocktakeId = :stocktakeId, i.version = i.version + 1
            WHERE i.id = :id AND i.locked = 0
            """)
    int lockForStocktake(@Param("id") Long id, @Param("stocktakeId") Long stocktakeId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Inventory i SET i.locked = 0, i.lockStocktakeId = NULL, i.version = i.version + 1
            WHERE i.lockStocktakeId = :stocktakeId
            """)
    int unlockByStocktake(@Param("stocktakeId") Long stocktakeId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Inventory i SET i.locked = 0, i.lockStocktakeId = NULL, i.version = i.version + 1
            WHERE i.id = :id AND i.locked = 1
            """)
    int unlockOne(@Param("id") Long id);

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

    @Query(value = """
            SELECT COALESCE(SUM(i.on_hand_qty * s.cost_price), 0)
              FROM inventory i
              JOIN product_sku s ON s.id = i.sku_id
            """, nativeQuery = true)
    BigDecimal sumOnHandAmount();

    @Query(value = """
            SELECT i.sku_id, s.sku_code, p.name,
                   SUM(i.on_hand_qty), SUM(i.on_hand_qty * s.cost_price)
              FROM inventory i
              JOIN product_sku s ON s.id = i.sku_id
              JOIN product p ON p.id = s.spu_id
             GROUP BY i.sku_id, s.sku_code, p.name
             ORDER BY SUM(i.on_hand_qty * s.cost_price) DESC
             LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> topSkusByAmount(@Param("limit") int limit);
}
