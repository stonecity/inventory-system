package com.dream.inventory.repository;

import com.dream.inventory.entity.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    boolean existsByCode(String code);

    @Query("""
            SELECT s FROM Supplier s
            WHERE (:keyword IS NULL OR s.code LIKE CONCAT('%', :keyword, '%') OR s.name LIKE CONCAT('%', :keyword, '%'))
              AND (:status IS NULL OR s.status = :status)
            """)
    Page<Supplier> search(@Param("keyword") String keyword, @Param("status") Integer status, Pageable pageable);
}
