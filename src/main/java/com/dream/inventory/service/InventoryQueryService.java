package com.dream.inventory.service;

import com.dream.inventory.common.PageResult;
import com.dream.inventory.dto.inventory.InventoryLogVO;
import com.dream.inventory.dto.inventory.InventoryVO;
import com.dream.inventory.entity.Inventory;
import com.dream.inventory.entity.InventoryLog;
import com.dream.inventory.entity.ProductSku;
import com.dream.inventory.entity.enums.InventoryChangeType;
import com.dream.inventory.repository.InventoryLogRepository;
import com.dream.inventory.repository.InventoryRepository;
import com.dream.inventory.repository.ProductSkuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryQueryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryLogRepository inventoryLogRepository;
    private final ProductSkuRepository skuRepository;
    private final WarehouseAccessService warehouseAccessService;

    public PageResult<InventoryVO> list(Long warehouseId, Long skuId, boolean lowStockOnly, int page, int size) {
        if (warehouseId != null) {
            warehouseAccessService.checkWarehouseAccess(warehouseId);
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by("warehouseId", "skuId"));
        Page<Inventory> result = inventoryRepository.search(warehouseId, skuId, lowStockOnly, 10, pageable);
        return PageResult.of(result.getContent().stream().map(this::toVO).toList(),
                result.getTotalElements(), page, size);
    }

    public List<InventoryVO> getBySku(Long skuId) {
        return inventoryRepository.findBySkuIdOrderByWarehouseIdAsc(skuId).stream()
                .map(this::toVO).toList();
    }

    public PageResult<InventoryLogVO> listLogs(Long skuId, Long warehouseId, InventoryChangeType changeType,
                                               Long movementId, Instant from, Instant to, int page, int size) {
        if (warehouseId != null) {
            warehouseAccessService.checkWarehouseAccess(warehouseId);
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "operatedAt"));
        Page<InventoryLog> result = inventoryLogRepository.search(skuId, warehouseId, changeType, movementId, from, to, pageable);
        return PageResult.of(result.getContent().stream().map(this::toLogVO).toList(),
                result.getTotalElements(), page, size);
    }

    private InventoryVO toVO(Inventory inv) {
        String skuCode = skuRepository.findById(inv.getSkuId()).map(ProductSku::getSkuCode).orElse("");
        return InventoryVO.builder()
                .id(inv.getId())
                .skuId(inv.getSkuId())
                .skuCode(skuCode)
                .warehouseId(inv.getWarehouseId())
                .locationId(inv.getLocationId())
                .onHandQty(inv.getOnHandQty())
                .reservedQty(inv.getReservedQty())
                .availableQty(inv.getAvailableQty())
                .inTransitQty(inv.getInTransitQty())
                .locked(inv.getLocked())
                .lockStocktakeId(inv.getLockStocktakeId())
                .version(inv.getVersion())
                .updatedAt(inv.getUpdatedAt())
                .build();
    }

    private InventoryLogVO toLogVO(InventoryLog log) {
        String skuCode = skuRepository.findById(log.getSkuId()).map(ProductSku::getSkuCode).orElse("");
        return InventoryLogVO.builder()
                .id(log.getId())
                .skuId(log.getSkuId())
                .skuCode(skuCode)
                .warehouseId(log.getWarehouseId())
                .locationId(log.getLocationId())
                .changeType(log.getChangeType())
                .deltaQty(log.getDeltaQty())
                .onHandBefore(log.getOnHandBefore())
                .onHandAfter(log.getOnHandAfter())
                .availableBefore(log.getAvailableBefore())
                .availableAfter(log.getAvailableAfter())
                .movementId(log.getMovementId())
                .operatorId(log.getOperatorId())
                .traceId(log.getTraceId())
                .remark(log.getRemark())
                .operatedAt(log.getOperatedAt())
                .build();
    }
}
