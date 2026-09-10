package com.dream.inventory.service;

import com.dream.inventory.common.BizException;
import com.dream.inventory.common.ErrorCode;
import com.dream.inventory.common.TraceContext;
import com.dream.inventory.entity.Inventory;
import com.dream.inventory.entity.InventoryLog;
import com.dream.inventory.entity.enums.InventoryChangeType;
import com.dream.inventory.repository.InventoryLogRepository;
import com.dream.inventory.repository.InventoryRepository;
import com.dream.inventory.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private static final int MAX_RETRIES = 3;

    private final InventoryRepository inventoryRepository;
    private final InventoryLogRepository inventoryLogRepository;

    @Transactional(rollbackFor = Exception.class)
    public Inventory getOrCreate(Long skuId, Long warehouseId, Long locationId) {
        Long loc = locationId != null ? locationId : 0L;
        return inventoryRepository.findBySkuIdAndWarehouseIdAndLocationId(skuId, warehouseId, loc)
                .orElseGet(() -> inventoryRepository.save(Inventory.builder()
                        .skuId(skuId)
                        .warehouseId(warehouseId)
                        .locationId(loc)
                        .onHandQty(0)
                        .reservedQty(0)
                        .availableQty(0)
                        .inTransitQty(0)
                        .locked(0)
                        .build()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void increase(Long skuId, Long warehouseId, Long locationId, int qty,
                         Long movementId, Long movementItemId, String remark) {
        execute(skuId, warehouseId, locationId, qty, InventoryChangeType.IN,
                movementId, movementItemId, null, remark,
                (id, ver, q) -> inventoryRepository.increase(id, ver, q));
    }

    @Transactional(rollbackFor = Exception.class)
    public void reserve(Long skuId, Long warehouseId, Long locationId, int qty,
                        Long movementId, Long movementItemId, String remark) {
        execute(skuId, warehouseId, locationId, qty, InventoryChangeType.RESERVE,
                movementId, movementItemId, null, remark,
                (id, ver, q) -> inventoryRepository.reserve(id, ver, q));
    }

    @Transactional(rollbackFor = Exception.class)
    public void release(Long skuId, Long warehouseId, Long locationId, int qty,
                        Long movementId, Long movementItemId, String remark) {
        execute(skuId, warehouseId, locationId, qty, InventoryChangeType.RELEASE,
                movementId, movementItemId, null, remark,
                (id, ver, q) -> inventoryRepository.releaseReserve(id, ver, q));
    }

    @Transactional(rollbackFor = Exception.class)
    public void deduct(Long skuId, Long warehouseId, Long locationId, int qty,
                       Long movementId, Long movementItemId, String remark) {
        execute(skuId, warehouseId, locationId, qty, InventoryChangeType.OUT,
                movementId, movementItemId, null, remark,
                (id, ver, q) -> inventoryRepository.deduct(id, ver, q));
    }

    @Transactional(rollbackFor = Exception.class)
    public void decreaseAvailable(Long skuId, Long warehouseId, Long locationId, int qty,
                                  Long movementId, Long movementItemId,
                                  InventoryChangeType changeType, String remark) {
        execute(skuId, warehouseId, locationId, qty, changeType,
                movementId, movementItemId, null, remark,
                (id, ver, q) -> inventoryRepository.decreaseAvailable(id, ver, q));
    }

    @Transactional(rollbackFor = Exception.class)
    public void adjustGain(Long skuId, Long warehouseId, Long locationId, int qty,
                           Long movementId, Long movementItemId, Long stocktakeId, String remark) {
        execute(skuId, warehouseId, locationId, qty, InventoryChangeType.ADJUST_GAIN,
                movementId, movementItemId, stocktakeId, remark,
                (id, ver, q) -> inventoryRepository.adjustGain(id, ver, q));
    }

    @Transactional(rollbackFor = Exception.class)
    public void adjustLoss(Long skuId, Long warehouseId, Long locationId, int qty,
                           Long movementId, Long movementItemId, Long stocktakeId, String remark) {
        execute(skuId, warehouseId, locationId, qty, InventoryChangeType.ADJUST_LOSS,
                movementId, movementItemId, stocktakeId, remark,
                (id, ver, q) -> inventoryRepository.adjustLoss(id, ver, q));
    }

    @Transactional(rollbackFor = Exception.class)
    public void transferOut(Long skuId, Long warehouseId, Long locationId, int qty,
                            Long movementId, Long movementItemId, String remark) {
        execute(skuId, warehouseId, locationId, qty, InventoryChangeType.TRANSFER_OUT,
                movementId, movementItemId, null, remark,
                (id, ver, q) -> inventoryRepository.decreaseAvailable(id, ver, q));
    }

    @Transactional(rollbackFor = Exception.class)
    public void addInTransit(Long skuId, Long toWarehouseId, int qty,
                             Long movementId, Long movementItemId, String remark) {
        execute(skuId, toWarehouseId, 0L, qty, InventoryChangeType.TRANSFER_OUT,
                movementId, movementItemId, null, remark,
                (id, ver, q) -> inventoryRepository.addInTransit(id, ver, q));
    }

    @Transactional(rollbackFor = Exception.class)
    public void transferIn(Long skuId, Long toWarehouseId, Long locationId, int qty,
                           Long movementId, Long movementItemId, String remark) {
        execute(skuId, toWarehouseId, locationId, qty, InventoryChangeType.TRANSFER_IN,
                movementId, movementItemId, null, remark,
                (id, ver, q) -> inventoryRepository.receiveInTransit(id, ver, q));
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancelInTransit(Long skuId, Long toWarehouseId, int qty,
                                Long movementId, Long movementItemId, String remark) {
        execute(skuId, toWarehouseId, 0L, qty, InventoryChangeType.TRANSFER_IN,
                movementId, movementItemId, null, remark,
                (id, ver, q) -> inventoryRepository.cancelInTransit(id, ver, q));
    }

    @Transactional(rollbackFor = Exception.class)
    public void returnIn(Long skuId, Long warehouseId, Long locationId, int qty,
                         Long movementId, Long movementItemId, String remark) {
        execute(skuId, warehouseId, locationId, qty, InventoryChangeType.RETURN_IN,
                movementId, movementItemId, null, remark,
                (id, ver, q) -> inventoryRepository.increase(id, ver, q));
    }

    @Transactional(rollbackFor = Exception.class)
    public void returnOut(Long skuId, Long warehouseId, Long locationId, int qty,
                          Long movementId, Long movementItemId, String remark) {
        execute(skuId, warehouseId, locationId, qty, InventoryChangeType.RETURN_OUT,
                movementId, movementItemId, null, remark,
                (id, ver, q) -> inventoryRepository.decreaseAvailable(id, ver, q));
    }

    @FunctionalInterface
    private interface InventoryUpdate {
        int apply(Long id, Integer version, int qty);
    }

    private void execute(Long skuId, Long warehouseId, Long locationId, int qty,
                         InventoryChangeType changeType,
                         Long movementId, Long movementItemId, Long stocktakeId,
                         String remark, InventoryUpdate update) {
        if (qty <= 0) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "数量必须大于 0");
        }
        Long loc = locationId != null ? locationId : 0L;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            Inventory inv = getOrCreate(skuId, warehouseId, loc);
            if (inv.getLocked() != null && inv.getLocked() == 1
                    && changeType != InventoryChangeType.ADJUST_GAIN
                    && changeType != InventoryChangeType.ADJUST_LOSS) {
                throw new BizException(ErrorCode.INVENTORY_LOCKED, "库存处于盘点锁定");
            }
            int onHandBefore = inv.getOnHandQty();
            int reservedBefore = inv.getReservedQty();
            int availableBefore = inv.getAvailableQty();
            int rows = update.apply(inv.getId(), inv.getVersion(), qty);
            if (rows == 1) {
                Inventory after = inventoryRepository.findById(inv.getId()).orElseThrow();
                writeLog(inv.getId(), skuId, warehouseId, loc, changeType, signedDelta(changeType, qty),
                        onHandBefore, after.getOnHandQty(),
                        reservedBefore, after.getReservedQty(),
                        availableBefore, after.getAvailableQty(),
                        movementId, movementItemId, stocktakeId, remark);
                return;
            }
            Inventory refreshed = inventoryRepository.findById(inv.getId()).orElseThrow();
            if (changeType == InventoryChangeType.RESERVE && refreshed.getAvailableQty() < qty) {
                throw new BizException(ErrorCode.INSUFFICIENT_STOCK);
            }
            if (changeType == InventoryChangeType.RESERVE && refreshed.getLocked() == 1) {
                throw new BizException(ErrorCode.INVENTORY_LOCKED);
            }
            sleepBackoff(attempt);
        }
        throw new BizException(ErrorCode.CONCURRENT_CONFLICT);
    }

    private int signedDelta(InventoryChangeType type, int qty) {
        return switch (type) {
            case IN, RELEASE, ADJUST_GAIN, TRANSFER_IN, RETURN_IN -> qty;
            case OUT, RESERVE, ADJUST_LOSS, TRANSFER_OUT, RETURN_OUT -> -qty;
        };
    }

    private void writeLog(Long inventoryId, Long skuId, Long warehouseId, Long locationId,
                          InventoryChangeType changeType, int deltaQty,
                          int onHandBefore, int onHandAfter,
                          int reservedBefore, int reservedAfter,
                          int availableBefore, int availableAfter,
                          Long movementId, Long movementItemId, Long stocktakeId, String remark) {
        Long operatorId = SecurityUtils.currentUser() != null
                ? SecurityUtils.currentUser().getId() : 0L;
        inventoryLogRepository.save(InventoryLog.builder()
                .inventoryId(inventoryId)
                .skuId(skuId)
                .warehouseId(warehouseId)
                .locationId(locationId)
                .changeType(changeType)
                .deltaQty(deltaQty)
                .onHandBefore(onHandBefore)
                .onHandAfter(onHandAfter)
                .reservedBefore(reservedBefore)
                .reservedAfter(reservedAfter)
                .availableBefore(availableBefore)
                .availableAfter(availableAfter)
                .movementId(movementId)
                .movementItemId(movementItemId)
                .stocktakeId(stocktakeId)
                .operatorId(operatorId)
                .traceId(TraceContext.getOrCreate())
                .remark(remark)
                .build());
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(20L * (1L << attempt));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.CONCURRENT_CONFLICT);
        }
    }
}
