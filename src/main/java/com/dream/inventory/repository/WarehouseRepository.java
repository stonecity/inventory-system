package com.dream.inventory.repository;

import com.dream.inventory.entity.Warehouse;
import com.dream.inventory.entity.enums.WarehouseType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    Optional<Warehouse> findByCode(String code);

    List<Warehouse> findByTypeAndStatus(WarehouseType type, Integer status);

    boolean existsByCodeAndIdNot(String code, Long id);

    @Query("SELECT w FROM Warehouse w WHERE "
            + "(:keyword IS NULL OR w.code LIKE %:keyword% OR w.name LIKE %:keyword% OR w.address LIKE %:keyword%) "
            + "AND (:type IS NULL OR w.type = :type) "
            + "AND (:status IS NULL OR w.status = :status)")
    Page<Warehouse> search(@Param("keyword") String keyword,
                           @Param("type") WarehouseType type,
                           @Param("status") Integer status,
                           Pageable pageable);
}
