package com.dream.inventory.repository;

import com.dream.inventory.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsByCategoryId(Long categoryId);

    long countByCategoryId(Long categoryId);

    List<Product> findByCategoryIdIn(Collection<Long> categoryIds);

    @Query("SELECT p.categoryId, COUNT(p) FROM Product p WHERE p.categoryId IS NOT NULL GROUP BY p.categoryId")
    List<Object[]> countGroupedByCategoryId();

    @Query("SELECT p FROM Product p WHERE (:keyword IS NULL OR p.name LIKE %:keyword% OR p.brand LIKE %:keyword%) "
            + "AND (:status IS NULL OR p.status = :status) "
            + "AND (:filterByCategory = 0 OR p.categoryId IN :categoryIds)")
    Page<Product> search(@Param("keyword") String keyword,
                         @Param("status") Integer status,
                         @Param("filterByCategory") int filterByCategory,
                         @Param("categoryIds") Collection<Long> categoryIds,
                         Pageable pageable);
}
