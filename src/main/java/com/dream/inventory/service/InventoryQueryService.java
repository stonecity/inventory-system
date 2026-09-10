package com.dream.inventory.service;

import com.dream.inventory.common.BizException;
import com.dream.inventory.common.CsvUtils;
import com.dream.inventory.common.ErrorCode;
import com.dream.inventory.common.PageResult;
import com.dream.inventory.dto.inventory.InventoryLogVO;
import com.dream.inventory.dto.inventory.InventoryVO;
import com.dream.inventory.entity.Inventory;
import com.dream.inventory.entity.InventoryLog;
import com.dream.inventory.entity.ProductSku;
import com.dream.inventory.entity.StocktakeItem;
import com.dream.inventory.entity.Warehouse;
import com.dream.inventory.entity.enums.InventoryChangeType;
import com.dream.inventory.repository.InventoryLogRepository;
import com.dream.inventory.repository.InventoryRepository;
import com.dream.inventory.repository.ProductSkuRepository;
import com.dream.inventory.repository.StockMovementRepository;
import com.dream.inventory.repository.StocktakeItemRepository;
import com.dream.inventory.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InventoryQueryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryLogRepository inventoryLogRepository;
    private final ProductSkuRepository skuRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockMovementRepository movementRepository;
    private final StocktakeItemRepository stocktakeItemRepository;
    private final InventoryService inventoryService;
    private final WarehouseAccessService warehouseAccessService;
    private final AuditLogService auditLogService;

    public PageResult<InventoryVO> list(Long warehouseId, Long skuId, Long categoryId, String keyword,
                                        boolean lowStockOnly, int page, int size) {
        if (warehouseId != null) {
            warehouseAccessService.checkWarehouseAccess(warehouseId);
        }
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        PageRequest pageable = PageRequest.of(page, size, Sort.by("warehouseId", "skuId"));
        Page<Inventory> result = inventoryRepository.search(
                warehouseId, skuId, lowStockOnly, 10, kw, categoryId, pageable);
        return PageResult.of(result.getContent().stream().map(this::toVO).toList(),
                result.getTotalElements(), page, size);
    }

    public List<InventoryVO> getBySku(Long skuId) {
        return inventoryRepository.findBySkuIdOrderByWarehouseIdAsc(skuId).stream()
                .map(this::toVO).toList();
    }

    public PageResult<InventoryLogVO> listLogs(Long skuId, Long warehouseId, InventoryChangeType changeType,
                                               Long movementId, String movementNo,
                                               Instant from, Instant to, int page, int size) {
        if (warehouseId != null) {
            warehouseAccessService.checkWarehouseAccess(warehouseId);
        }
        String no = StringUtils.hasText(movementNo) ? movementNo.trim() : null;
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "operatedAt"));
        Page<InventoryLog> result = inventoryLogRepository.search(
                skuId, warehouseId, changeType, movementId, no, from, to, pageable);
        return PageResult.of(result.getContent().stream().map(this::toLogVO).toList(),
                result.getTotalElements(), page, size);
    }

    public byte[] exportLogs(Long skuId, Long warehouseId, InventoryChangeType changeType,
                             Long movementId, String movementNo, Instant from, Instant to) {
        PageResult<InventoryLogVO> page = listLogs(
                skuId, warehouseId, changeType, movementId, movementNo, from, to, 0, 10_000);
        StringBuilder sb = new StringBuilder();
        sb.append(CsvUtils.row("时间", "SKU", "仓库", "类型", "变动量",
                "在库前", "在库后", "可用前", "可用后", "单据号", "traceId", "备注")).append('\n');
        for (InventoryLogVO log : page.getItems()) {
            sb.append(CsvUtils.row(
                    log.getOperatedAt(), log.getSkuCode(), log.getWarehouseId(),
                    log.getChangeType(), log.getDeltaQty(),
                    log.getOnHandBefore(), log.getOnHandAfter(),
                    log.getAvailableBefore(), log.getAvailableAfter(),
                    log.getMovementNo(), log.getTraceId(), log.getRemark()
            )).append('\n');
        }
        return CsvUtils.toUtf8Bom(sb.toString());
    }

    @Transactional(rollbackFor = Exception.class)
    public InventoryVO unlock(Long id) {
        if (!warehouseAccessService.isAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅管理员可临时解锁");
        }
        Inventory before = inventoryRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "库存记录不存在"));
        warehouseAccessService.checkWarehouseAccess(before.getWarehouseId());
        Long stocktakeId = before.getLockStocktakeId();
        Inventory inv = inventoryService.unlockOne(id);
        if (stocktakeId != null) {
            for (StocktakeItem item : stocktakeItemRepository.findByStocktakeIdOrderBySkuIdAsc(stocktakeId)) {
                if (item.getSkuId().equals(inv.getSkuId())
                        && item.getLocationId().equals(inv.getLocationId())) {
                    item.setExcluded(1);
                    stocktakeItemRepository.save(item);
                }
            }
        }
        auditLogService.log("UNLOCK_INVENTORY", "inventory", String.valueOf(id),
                Map.of("skuId", inv.getSkuId(), "warehouseId", inv.getWarehouseId(),
                        "stocktakeId", stocktakeId == null ? 0 : stocktakeId));
        return toVO(inv);
    }

    private InventoryVO toVO(Inventory inv) {
        String skuCode = skuRepository.findById(inv.getSkuId()).map(ProductSku::getSkuCode).orElse("");
        String warehouseName = warehouseRepository.findById(inv.getWarehouseId())
                .map(Warehouse::getName).orElse("");
        return InventoryVO.builder()
                .id(inv.getId())
                .skuId(inv.getSkuId())
                .skuCode(skuCode)
                .warehouseId(inv.getWarehouseId())
                .warehouseName(warehouseName)
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
        String movementNo = log.getMovementId() == null ? null
                : movementRepository.findById(log.getMovementId())
                .map(m -> m.getMovementNo()).orElse(null);
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
                .movementNo(movementNo)
                .operatorId(log.getOperatorId())
                .traceId(log.getTraceId())
                .remark(log.getRemark())
                .operatedAt(log.getOperatedAt())
                .build();
    }
}
