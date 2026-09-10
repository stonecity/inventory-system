package com.dream.inventory.repository;

import com.dream.inventory.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, Long> {

    List<Location> findByWarehouseIdOrderByCodeAsc(Long warehouseId);

    Optional<Location> findByWarehouseIdAndCode(Long warehouseId, String code);

    boolean existsByWarehouseIdAndCode(Long warehouseId, String code);
}
