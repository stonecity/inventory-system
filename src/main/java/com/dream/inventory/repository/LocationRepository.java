package com.dream.inventory.repository;

import com.dream.inventory.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, Long> {

    List<Location> findByWarehouseIdOrderByCodeAsc(Long warehouseId);

    Optional<Location> findByWarehouseIdAndCode(Long warehouseId, String code);

    boolean existsByWarehouseIdAndCode(Long warehouseId, String code);

    long countByWarehouseId(Long warehouseId);

    @Query("SELECT l.warehouseId AS warehouseId, COUNT(l) AS cnt FROM Location l WHERE l.warehouseId IN :ids GROUP BY l.warehouseId")
    List<LocationCountView> countGroupedByWarehouseId(@Param("ids") Collection<Long> ids);

    interface LocationCountView {
        Long getWarehouseId();
        Long getCnt();
    }
}
