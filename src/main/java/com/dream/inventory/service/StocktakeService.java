package com.dream.inventory.service;

import com.dream.inventory.common.BizException;
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

@Service
@RequiredArgsConstructor
public class StocktakeService {

    private final StocktakeRepository stocktakeRepository;
    private final StocktakeItemRepository stocktakeItemRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductSkuRepository skuRepository;
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
        for (Inventory inv : inventories) {
            if (inv.getLocked() != null && inv.getLocked() == 1) {
                throw new BizException(ErrorCode.STOCKTAKE_SCOPE_OVERLAP, "部分库存已被其他盘点锁定");
            }
            inv.setLocked(1);
            inv.setLockStocktakeId(st.getId());
            inventoryRepository.save(inv);
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
            if (item.getCountedQty() == null) continue;
            int diff = item.getDiffQty();
            if (diff > 0) gainQty += diff;
            else if (diff < 0) lossQty += -diff;
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
    public StocktakeVO approve(Long id) {
        Stocktake st = findOrThrow(id);
        if (st.getStatus() != StocktakeStatus.PENDING_APPROVAL) {
            throw new BizException(ErrorCode.ILLEGAL_STATE_TRANSITION);
        }
        if (!warehouseAccessService.isAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅管理员可审批盘点");
        }
        TraceContext.getOrCreate();
        List<StocktakeItem> items = stocktakeItemRepository.findByStocktakeIdOrderBySkuIdAsc(id);
        for (StocktakeItem item : items) {
            if (item.getExcluded() == 1 || item.getDiffQty() == null || item.getDiffQty() == 0) continue;
            int diff = item.getDiffQty();
            Inventory inv = inventoryService.getOrCreate(item.getSkuId(), st.getWarehouseId(), item.getLocationId());
            if (diff < 0 && inv.getOnHandQty() + diff < inv.getReservedQty()) {
                throw new BizException(ErrorCode.LOSS_EXCEEDS_RESERVED);
            }
            if (diff > 0) {
                inventoryService.adjustGain(item.getSkuId(), st.getWarehouseId(), item.getLocationId(),
                        diff, null, null, st.getId(), "盘点盘盈");
            } else {
                inventoryService.adjustLoss(item.getSkuId(), st.getWarehouseId(), item.getLocationId(),
                        -diff, null, null, st.getId(), "盘点盘亏");
            }
        }
        for (Inventory inv : inventoryRepository.findByLockStocktakeId(st.getId())) {
            inv.setLocked(0);
            inv.setLockStocktakeId(null);
            inventoryRepository.save(inv);
        }
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
        for (Inventory inv : inventoryRepository.findByLockStocktakeId(st.getId())) {
            inv.setLocked(0);
            inv.setLockStocktakeId(null);
            inventoryRepository.save(inv);
        }
        st.setStatus(StocktakeStatus.CANCELLED);
        st.setCancelledAt(Instant.now());
        return toVO(stocktakeRepository.save(st));
    }

    private List<Inventory> resolveScopeInventories(Stocktake st) {
        if (st.getScope() == StocktakeScope.ALL) {
            return inventoryRepository.search(st.getWarehouseId(), null, false, 0,
                    PageRequest.of(0, Integer.MAX_VALUE)).getContent();
        }
        return inventoryRepository.search(st.getWarehouseId(), null, false, 0,
                PageRequest.of(0, Integer.MAX_VALUE)).getContent();
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
