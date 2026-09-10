package com.dream.inventory.repository;

import com.dream.inventory.entity.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

    List<ProductCategory> findAllByOrderBySortOrderAscIdAsc();

    boolean existsByParentId(Long parentId);

    List<ProductCategory> findByParentId(Long parentId);
}
