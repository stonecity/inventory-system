package com.dream.inventory.service;

import com.dream.inventory.common.BizException;
import com.dream.inventory.common.ErrorCode;
import com.dream.inventory.dto.location.*;
import com.dream.inventory.entity.Location;
import com.dream.inventory.repository.LocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository locationRepository;
    private final WarehouseService warehouseService;

    public List<LocationVO> listByWarehouse(Long warehouseId) {
        warehouseService.getById(warehouseId);
        return locationRepository.findByWarehouseIdOrderByCodeAsc(warehouseId).stream()
                .map(this::toVO)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public LocationBatchCreateResult batchCreate(Long warehouseId, LocationBatchCreateRequest request) {
        warehouseService.getById(warehouseId);
        String zone = request.getZone().trim().toUpperCase();
        int created = 0;
        int skipped = 0;
        List<LocationVO> createdList = new ArrayList<>();

        for (int row = 1; row <= request.getRows(); row++) {
            for (int layer = 1; layer <= request.getLayers(); layer++) {
                String code = String.format("%s-%02d-%02d", zone, row, layer);
                if (locationRepository.existsByWarehouseIdAndCode(warehouseId, code)) {
                    skipped++;
                    continue;
                }
                Location location = Location.builder()
                        .warehouseId(warehouseId)
                        .code(code)
                        .zone(zone)
                        .shelf(String.valueOf(row))
                        .status(1)
                        .isDefault(0)
                        .build();
                location = locationRepository.save(location);
                createdList.add(toVO(location));
                created++;
            }
        }
        return LocationBatchCreateResult.builder()
                .created(created)
                .skipped(skipped)
                .locations(createdList)
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public LocationVO update(Long id, LocationUpdateRequest request) {
        Location location = findOrThrow(id);
        if (request.getZone() != null) {
            location.setZone(request.getZone());
        }
        if (request.getShelf() != null) {
            location.setShelf(request.getShelf());
        }
        if (request.getIsDefault() != null) {
            if (request.getIsDefault() == 1) {
                clearDefaultFlag(location.getWarehouseId());
            }
            location.setIsDefault(request.getIsDefault());
        }
        if (request.getStatus() != null) {
            if (request.getStatus() != 0 && request.getStatus() != 1) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "状态值必须为 0 或 1");
            }
            location.setStatus(request.getStatus());
        }
        return toVO(locationRepository.save(location));
    }

    private void clearDefaultFlag(Long warehouseId) {
        locationRepository.findByWarehouseIdOrderByCodeAsc(warehouseId).forEach(loc -> {
            if (loc.getIsDefault() != null && loc.getIsDefault() == 1) {
                loc.setIsDefault(0);
                locationRepository.save(loc);
            }
        });
    }

    private Location findOrThrow(Long id) {
        return locationRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "库位不存在"));
    }

    private LocationVO toVO(Location location) {
        return LocationVO.builder()
                .id(location.getId())
                .warehouseId(location.getWarehouseId())
                .code(location.getCode())
                .zone(location.getZone())
                .shelf(location.getShelf())
                .isDefault(location.getIsDefault())
                .status(location.getStatus())
                .createdAt(location.getCreatedAt())
                .updatedAt(location.getUpdatedAt())
                .build();
    }
}
