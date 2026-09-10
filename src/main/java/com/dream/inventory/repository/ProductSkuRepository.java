package com.dream.inventory.repository;

import com.dream.inventory.entity.ProductSku;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductSkuRepository extends JpaRepository<ProductSku, Long> {

    List<ProductSku> findBySpuId(Long spuId);

    Page<ProductSku> findBySpuId(Long spuId, Pageable pageable);

    Optional<ProductSku> findBySkuCode(String skuCode);

    Optional<ProductSku> findByBarcode(String barcode);

    boolean existsBySkuCodeAndIdNot(String skuCode, Long id);

    boolean existsByBarcodeAndIdNot(String barcode, Long id);

    @Query("SELECT s FROM ProductSku s WHERE "
            + "(:keyword IS NULL OR s.skuCode LIKE %:keyword% OR s.barcode LIKE %:keyword%) "
            + "AND (:spuId IS NULL OR s.spuId = :spuId) "
            + "AND (:status IS NULL OR s.status = :status)")
    Page<ProductSku> search(@Param("keyword") String keyword,
                            @Param("spuId") Long spuId,
                            @Param("status") Integer status,
                            Pageable pageable);
}
