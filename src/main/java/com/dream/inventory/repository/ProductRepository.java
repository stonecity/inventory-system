package com.dream.inventory.repository;

import com.dream.inventory.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsByCategoryId(Long categoryId);

    @Query("SELECT p FROM Product p WHERE (:keyword IS NULL OR p.name LIKE %:keyword% OR p.brand LIKE %:keyword%) "
            + "AND (:status IS NULL OR p.status = :status)")
    Page<Product> search(@Param("keyword") String keyword, @Param("status") Integer status, Pageable pageable);
}
