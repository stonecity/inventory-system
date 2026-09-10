package com.dream.inventory.service;

import com.dream.inventory.common.BizException;
import com.dream.inventory.common.CsvUtils;
import com.dream.inventory.common.ErrorCode;
import com.dream.inventory.common.PageResult;
import com.dream.inventory.common.TraceContext;
import com.dream.inventory.dto.stocktake.*;
import com.dream.inventory.entity.*;
import com.dream.inventory.entity.enums.*;
import com.dream.inventory.repository.*;
import com.dream.inventory.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StocktakeService {

    private final StocktakeRepository stocktakeRepository;
    private final StocktakeItemRepository stocktakeItemRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductSkuRepository skuRepository;
    private final ProductRepository productRepository;
    private final StockMovementRepository movementRepository;
    private final InventoryService inventoryService;
    private final DocSequenceService docSequenceService;
    private final WarehouseAccessService warehouseAccessService;
    private final AuditLogService auditLogService;
    private final AlertService alertService;

    public PageResult<StocktakeVO> list(Long warehouseId, StocktakeStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Stocktake> result = stocktakeRepository.search(warehouseId, status, pageable);
        return PageResult.of(result.getContent().stream().map(this::toVO).toList(),
                result.getTotalElements(), page, size);
    }

    public StocktakeVO getById(Long id) {
        return toVO(findOrThrow(id));
    }

    public List<StocktakeItemVO> getItems(Long id) {
        findOrThrow(id);
        return stocktakeItemRepository.findByStocktakeIdOrderBySkuIdAsc(id).stream()
                .map(this::toItemVO).toList();
    }

    public List<String> precheck(StocktakeCreateRequest req) {
        List<String> blocked = new ArrayList<>();
        List<StockMovement> movements = movementRepository.findByWarehouseIdAndStatusIn(
                req.getWarehouseId(), List.of(MovementStatus.RECEIVING, MovementStatus.PICKING));
        for (StockMovement m : movements) {
            blocked.add(m.getMovementNo());
        }
        return blocked;
    }

    @Transactional(rollbackFor = Exception.class)
    public StocktakeVO create(StocktakeCreateRequest req) {
        warehouseAccessService.checkWarehouseAccess(req.getWarehouseId());
        if (stocktakeRepository.existsByWarehouseIdAndStatusIn(req.getWarehouseId(),
                List.of(StocktakeStatus.CREATED, StocktakeStatus.LOCKED, StocktakeStatus.COUNTING,
                        StocktakeStatus.PENDING_APPROVAL))) {
            throw new BizException(ErrorCode.STOCKTAKE_SCOPE_OVERLAP);
        }
        List<String> blocked = precheck(req);
        if (!blocked.isEmpty()) {
            throw new BizException(ErrorCode.STOCKTAKE_BLOCKED_BY_MOVEMENT,
                    "存在执行中单据: " + String.join(", ", blocked));
        }
        Stocktake st = Stocktake.builder()
                .stocktakeNo(docSequenceService.nextNo("ST"))
                .warehouseId(req.getWarehouseId())
                .scope(req.getScope())
                .scopeValue(req.getScopeValue())
                .creatorId(SecurityUtils.currentUser().getId())
                .remark(req.getRemark())
                .build();
        return toVO(stocktakeRepository.save(st));
    }

    @Transactional(rollbackFor = Exception.class)
    public StocktakeVO lock(Long id) {
        Stocktake st = findOrThrow(id);
        warehouseAccessService.checkWarehouseAccess(st.getWarehouseId());
        if (st.getStatus() != StocktakeStatus.CREATED) {
            throw new BizException(ErrorCode.ILLEGAL_STATE_TRANSITION);
        }
        TraceContext.getOrCreate();
        List<Inventory> inventories = resolveScopeInventories(st);
        if (inventories.isEmpty()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "盘点范围内没有库存记录");
        }
        for (Inventory inv : inventories) {
            inventoryService.lockForStocktake(inv.getId(), st.getId());
            StocktakeItem item = StocktakeItem.builder()
                    .stocktake(st)
                    .skuId(inv.getSkuId())
                    .locationId(inv.getLocationId())
                    .snapshotQty(inv.getOnHandQty())
                    .build();
            stocktakeItemRepository.save(item);
        }
        st.setStatus(StocktakeStatus.LOCKED);
        st.setSnapshotAt(Instant.now());
        return toVO(stocktakeRepository.save(st));
    }

    @Transactional(rollbackFor = Exception.class)
    public StocktakeItemVO updateItem(Long stocktakeId, Long itemId, StocktakeCountRequest req) {
        Stocktake st = findOrThrow(stocktakeId);
        if (st.getStatus() != StocktakeStatus.LOCKED && st.getStatus() != StocktakeStatus.COUNTING) {
            throw new BizException(ErrorCode.ILLEGAL_STATE_TRANSITION);
        }
        StocktakeItem item = stocktakeItemRepository.findById(itemId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        item.setCountedQty(req.getCountedQty());
        item.setDiffQty(req.getCountedQty() - item.getSnapshotQty());
        item.setDiffReason(req.getReason());
        item.setCountedBy(SecurityUtils.currentUser().getId());
        item.setCountedAt(Instant.now());
        if (st.getStatus() == StocktakeStatus.LOCKED) {
            st.setStatus(StocktakeStatus.COUNTING);
            stocktakeRepository.save(st);
        }
        return toItemVO(stocktakeItemRepository.save(item));
    }

    @Transactional(rollbackFor = Exception.class)
    public StocktakeVO submit(Long id) {
        Stocktake st = findOrThrow(id);
        if (st.getStatus() != StocktakeStatus.COUNTING) {
            throw new BizException(ErrorCode.ILLEGAL_STATE_TRANSITION);
        }
        int gainQty = 0, lossQty = 0;
        BigDecimal gainAmount = BigDecimal.ZERO, lossAmount = BigDecimal.ZERO;
        for (StocktakeItem item : stocktakeItemRepository.findByStocktakeIdOrderBySkuIdAsc(id)) {
            if (item.getExcluded() == 1 || item.getCountedQty() == null) continue;
            int diff = item.getDiffQty() == null ? 0 : item.getDiffQty();
            BigDecimal cost = skuRepository.findById(item.getSkuId())
                    .map(ProductSku::getCostPrice).orElse(BigDecimal.ZERO);
            if (diff > 0) {
                gainQty += diff;
                gainAmount = gainAmount.add(cost.multiply(BigDecimal.valueOf(diff)));
            } else if (diff < 0) {
                lossQty += -diff;
                lossAmount = lossAmount.add(cost.multiply(BigDecimal.valueOf(-diff)));
            }
        }
        st.setGainQty(gainQty);
        st.setLossQty(lossQty);
        st.setGainAmount(gainAmount);
        st.setLossAmount(lossAmount);
        st.setStatus(StocktakeStatus.PENDING_APPROVAL);
        st.setSubmittedAt(Instant.now());
        return toVO(stocktakeRepository.save(st));
    }

    @Transactional(rollbackFor = Exception.class)
    public StocktakeVO approve(Long id, Integer version) {
        Stocktake st = findOrThrow(id);
        if (version != null && !version.equals(st.getVersion())) {
            throw new BizException(ErrorCode.STATE_CONFLICT);
        }
        if (st.getStatus() != StocktakeStatus.PENDING_APPROVAL) {
            throw new BizException(ErrorCode.ILLEGAL_STATE_TRANSITION);
        }
        if (!warehouseAccessService.isAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅管理员可审批盘点");
        }
        TraceContext.getOrCreate();
        List<StocktakeItem> items = stocktakeItemRepository.findByStocktakeIdOrderBySkuIdAsc(id);
        StockMovement adjust = StockMovement.builder()
                .movementNo(docSequenceService.nextNo(MovementType.ADJUST.docPrefix()))
                .type(MovementType.ADJUST)
                .status(MovementStatus.COMPLETED)
                .warehouseId(st.getWarehouseId())
                .refStocktakeId(st.getId())
                .creatorId(SecurityUtils.currentUser().getId())
                .approverId(SecurityUtils.currentUser().getId())
                .executorId(SecurityUtils.currentUser().getId())
                .approvedAt(Instant.now())
                .executedAt(Instant.now())
                .remark("盘点调整 " + st.getStocktakeNo())
                .build();
        int totalQty = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (StocktakeItem item : items) {
            if (item.getExcluded() == 1 || item.getDiffQty() == null || item.getDiffQty() == 0) continue;
            int diff = item.getDiffQty();
            Inventory inv = inventoryService.getOrCreate(item.getSkuId(), st.getWarehouseId(), item.getLocationId());
            if (diff < 0 && inv.getOnHandQty() + diff < inv.getReservedQty()) {
                throw new BizException(ErrorCode.LOSS_EXCEEDS_RESERVED);
            }
            ProductSku sku = skuRepository.findById(item.getSkuId()).orElse(null);
            BigDecimal price = sku != null ? sku.getCostPrice() : BigDecimal.ZERO;
            StockMovementItem mi = StockMovementItem.builder()
                    .skuId(item.getSkuId())
                    .locationId(item.getLocationId())
                    .plannedQty(Math.abs(diff))
                    .actualQty(Math.abs(diff))
                    .unitPrice(price)
                    .amount(price.multiply(BigDecimal.valueOf(Math.abs(diff))))
                    .remark(diff > 0 ? "盘盈" : "盘亏")
                    .build();
            adjust.addItem(mi);
            totalQty += Math.abs(diff);
            totalAmount = totalAmount.add(mi.getAmount());
        }
        adjust.setTotalQty(totalQty);
        adjust.setTotalAmount(totalAmount);
        adjust = movementRepository.save(adjust);
        for (StockMovementItem mi : adjust.getItems()) {
            int signed = items.stream()
                    .filter(i -> i.getSkuId().equals(mi.getSkuId())
                            && Objects.equals(i.getLocationId(), mi.getLocationId())
                            && i.getExcluded() != 1)
                    .map(StocktakeItem::getDiffQty)
                    .findFirst().orElse(0);
            if (signed > 0) {
                inventoryService.adjustGain(mi.getSkuId(), st.getWarehouseId(), mi.getLocationId(),
                        signed, adjust.getId(), mi.getId(), st.getId(), "盘点盘盈");
            } else if (signed < 0) {
                inventoryService.adjustLoss(mi.getSkuId(), st.getWarehouseId(), mi.getLocationId(),
                        -signed, adjust.getId(), mi.getId(), st.getId(), "盘点盘亏");
            }
        }
        inventoryService.unlockByStocktake(st.getId());
        st.setAdjustMovementId(adjust.getId());
        st.setStatus(StocktakeStatus.COMPLETED);
        st.setApproverId(SecurityUtils.currentUser().getId());
        st.setApprovedAt(Instant.now());
        auditLogService.log("APPROVE_STOCKTAKE", "stocktake", String.valueOf(id), Map.of("no", st.getStocktakeNo()));
        alertService.scanWarehouse(st.getWarehouseId());
        return toVO(stocktakeRepository.save(st));
    }

    @Transactional(rollbackFor = Exception.class)
    public StocktakeVO reject(Long id, String reason) {
        Stocktake st = findOrThrow(id);
        if (st.getStatus() != StocktakeStatus.PENDING_APPROVAL) {
            throw new BizException(ErrorCode.ILLEGAL_STATE_TRANSITION);
        }
        st.setStatus(StocktakeStatus.COUNTING);
        st.setRejectReason(reason);
        return toVO(stocktakeRepository.save(st));
    }

    @Transactional(rollbackFor = Exception.class)
    public StocktakeVO cancel(Long id) {
        Stocktake st = findOrThrow(id);
        if (st.getStatus() == StocktakeStatus.COMPLETED || st.getStatus() == StocktakeStatus.CANCELLED) {
            throw new BizException(ErrorCode.ILLEGAL_STATE_TRANSITION);
        }
        inventoryService.unlockByStocktake(st.getId());
        st.setStatus(StocktakeStatus.CANCELLED);
        st.setCancelledAt(Instant.now());
        return toVO(stocktakeRepository.save(st));
    }

    @Transactional(rollbackFor = Exception.class)
    public int importItems(Long id, List<StocktakeImportItemRequest> rows) {
        Stocktake st = findOrThrow(id);
        if (st.getStatus() != StocktakeStatus.LOCKED && st.getStatus() != StocktakeStatus.COUNTING) {
            throw new BizException(ErrorCode.ILLEGAL_STATE_TRANSITION);
        }
        List<StocktakeItem> items = stocktakeItemRepository.findByStocktakeIdOrderBySkuIdAsc(id);
        int updated = 0;
        for (StocktakeImportItemRequest row : rows) {
            ProductSku sku = skuRepository.findBySkuCode(row.getSkuCode())
                    .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "SKU 不存在: " + row.getSkuCode()));
            Long loc = row.getLocationId() != null ? row.getLocationId() : 0L;
            StocktakeItem item = items.stream()
                    .filter(i -> i.getSkuId().equals(sku.getId()) && i.getLocationId().equals(loc))
                    .findFirst()
                    .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND,
                            "盘点明细中不存在 SKU " + row.getSkuCode()));
            StocktakeCountRequest req = new StocktakeCountRequest();
            req.setCountedQty(row.getCountedQty());
            req.setReason(row.getReason());
            updateItem(id, item.getId(), req);
            updated++;
        }
        return updated;
    }

    public byte[] report(Long id) {
        Stocktake st = findOrThrow(id);
        List<StocktakeItem> items = stocktakeItemRepository.findByStocktakeIdOrderBySkuIdAsc(id);
        StringBuilder sb = new StringBuilder();
        sb.append(CsvUtils.row("盘点单号", st.getStocktakeNo())).append('\n');
        sb.append(CsvUtils.row("仓库", st.getWarehouseId(), "状态", st.getStatus(),
                "盘盈数量", st.getGainQty(), "盘亏数量", st.getLossQty())).append('\n');
        sb.append(CsvUtils.row("SKU", "库位", "快照", "实盘", "差异", "原因", "排除")).append('\n');
        for (StocktakeItem item : items) {
            String skuCode = skuRepository.findById(item.getSkuId()).map(ProductSku::getSkuCode).orElse("");
            sb.append(CsvUtils.row(skuCode, item.getLocationId(), item.getSnapshotQty(),
                    item.getCountedQty(), item.getDiffQty(), item.getDiffReason(), item.getExcluded())).append('\n');
        }
        return CsvUtils.toUtf8Bom(sb.toString());
    }

    private List<Inventory> resolveScopeInventories(Stocktake st) {
        List<Inventory> all = inventoryRepository.findByWarehouseId(st.getWarehouseId());
        if (st.getScope() == null || st.getScope() == StocktakeScope.ALL) {
            return all;
        }
        List<Long> values = st.getScopeValue() == null ? List.of() : st.getScopeValue();
        if (values.isEmpty()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "请提供盘点范围值");
        }
        return switch (st.getScope()) {
            case BY_SKU -> all.stream().filter(i -> values.contains(i.getSkuId())).toList();
            case BY_LOCATION -> all.stream().filter(i -> values.contains(i.getLocationId())).toList();
            case BY_CATEGORY -> {
                List<Long> spuIds = productRepository.findByCategoryIdIn(values).stream()
                        .map(Product::getId).toList();
                Set<Long> skuIds = skuRepository.findBySpuIdIn(spuIds).stream()
                        .map(ProductSku::getId).collect(Collectors.toSet());
                yield all.stream().filter(i -> skuIds.contains(i.getSkuId())).toList();
            }
            case ALL -> all;
        };
    }

    private Stocktake findOrThrow(Long id) {
        return stocktakeRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "盘点单不存在"));
    }

    private StocktakeVO toVO(Stocktake st) {
        return StocktakeVO.builder()
                .id(st.getId())
                .stocktakeNo(st.getStocktakeNo())
                .warehouseId(st.getWarehouseId())
                .scope(st.getScope())
                .status(st.getStatus())
                .gainQty(st.getGainQty())
                .lossQty(st.getLossQty())
                .gainAmount(st.getGainAmount())
                .lossAmount(st.getLossAmount())
                .adjustMovementId(st.getAdjustMovementId())
                .remark(st.getRemark())
                .rejectReason(st.getRejectReason())
                .snapshotAt(st.getSnapshotAt())
                .version(st.getVersion())
                .createdAt(st.getCreatedAt())
                .build();
    }

    private StocktakeItemVO toItemVO(StocktakeItem item) {
        String skuCode = skuRepository.findById(item.getSkuId()).map(ProductSku::getSkuCode).orElse("");
        return StocktakeItemVO.builder()
                .id(item.getId())
                .skuId(item.getSkuId())
                .skuCode(skuCode)
                .locationId(item.getLocationId())
                .snapshotQty(item.getSnapshotQty())
                .countedQty(item.getCountedQty())
                .diffQty(item.getDiffQty())
                .diffReason(item.getDiffReason())
                .excluded(item.getExcluded())
                .build();
    }
}
