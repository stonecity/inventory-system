package com.dream.inventory.repository;

import com.dream.inventory.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    boolean existsByCode(String code);

    @Query("""
            SELECT c FROM Customer c
            WHERE (:keyword IS NULL OR c.code LIKE CONCAT('%', :keyword, '%') OR c.name LIKE CONCAT('%', :keyword, '%'))
              AND (:status IS NULL OR c.status = :status)
            """)
    Page<Customer> search(@Param("keyword") String keyword, @Param("status") Integer status, Pageable pageable);
}
