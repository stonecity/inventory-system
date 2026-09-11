package com.dream.inventory.service;

import com.dream.inventory.common.BizException;
import com.dream.inventory.common.ErrorCode;
import com.dream.inventory.common.PageResult;
import com.dream.inventory.common.TraceContext;
import com.dream.inventory.dto.inventory.InventoryLogVO;
import com.dream.inventory.dto.movement.*;
import com.dream.inventory.entity.*;
import com.dream.inventory.entity.enums.*;
import com.dream.inventory.repository.*;
import com.dream.inventory.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockMovementService {

    private final StockMovementRepository movementRepository;
    private final StockMovementItemRepository movementItemRepository;
    private final ProductSkuRepository skuRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryLogRepository inventoryLogRepository;
    private final InventoryService inventoryService;
    private final DocSequenceService docSequenceService;
    private final WarehouseAccessService warehouseAccessService;
    private final AuditLogService auditLogService;
    private final AlertService alertService;
    private final WarehouseRepository warehouseRepository;
    private final SupplierRepository supplierRepository;
    private final CustomerRepository customerRepository;
    private final StockAlertRepository alertRepository;

    @Transactional(readOnly = true)
    public PageResult<MovementVO> list(MovementType type, MovementStatus status, Long warehouseId,
                                       Long partnerId, Instant from, Instant to, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<StockMovement> result = movementRepository.search(type, status, warehouseId, partnerId, from, to, pageable);
        Map<Long, List<StockMovementItem>> itemsByMovement = loadItemsByMovementIds(
                result.getContent().stream().map(StockMovement::getId).toList());
        return PageResult.of(result.getContent().stream()
                        .map(m -> toVO(m, itemsByMovement.getOrDefault(m.getId(), List.of())))
                        .toList(),
                result.getTotalElements(), page, size);
    }

    @Transactional(readOnly = true)
    public MovementVO getById(Long id) {
        return toVO(findOrThrow(id));
    }

    public List<InventoryLogVO> getLogs(Long id) {
        findOrThrow(id);
        return inventoryLogRepository.findByMovementIdOrderByOperatedAtAsc(id).stream()
                .map(this::toLogVO).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public MovementVO createPurchaseIn(MovementCreateRequest req) {
        return createMovement(req, MovementType.PURCHASE_IN, PartnerType.SUPPLIER, MovementStatus.DRAFT);
    }

    @Transactional(rollbackFor = Exception.class)
    public MovementVO createSaleOut(MovementCreateRequest req) {
        return createMovement(req, MovementType.SALE_OUT, PartnerType.CUSTOMER, MovementStatus.DRAFT);
    }

    @Transactional(rollbackFor = Exception.class)
    public MovementVO createTransfer(MovementCreateRequest req) {
        if (req.getToWarehouseId() == null || req.getToWarehouseId().equals(req.getWarehouseId())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "调拨目标仓库无效");
        }
        return createMovement(req, MovementType.TRANSFER, null, MovementStatus.DRAFT);
    }

    @Transactional(rollbackFor = Exception.class)
    public MovementVO createSaleReturn(MovementCreateRequest req) {
        if (req.getRefMovementId() == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "必须关联原销售出库单");
        }
        StockMovement ref = findOrThrow(req.getRefMovementId());
        if (ref.getType() != MovementType.SALE_OUT || ref.getStatus() != MovementStatus.SHIPPED) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "原单必须是已出库的销售单");
        }
        validateReturnQty(ref, req.getItems());
        MovementCreateRequest copy = req;
        copy.setWarehouseId(ref.getWarehouseId());
        return createMovement(copy, MovementType.SALE_RETURN, PartnerType.CUSTOMER, MovementStatus.DRAFT);
    }

    @Transactional(rollbackFor = Exception.class)
    public MovementVO createPurchaseReturn(MovementCreateRequest req) {
        if (req.getRefMovementId() == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "必须关联原采购入库单");
        }
        StockMovement ref = findOrThrow(req.getRefMovementId());
        if (ref.getType() != MovementType.PURCHASE_IN || ref.getStatus() != MovementStatus.COMPLETED) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "原单必须是已完成的采购入库单");
        }
        validateReturnQty(ref, req.getItems());
        MovementCreateRequest copy = req;
        copy.setWarehouseId(ref.getWarehouseId());
        return createMovement(copy, MovementType.PURCHASE_RETURN, PartnerType.SUPPLIER, MovementStatus.DRAFT);
    }

    @Transactional(rollbackFor = Exception.class)
    public MovementVO createOtherIn(MovementCreateRequest req) {
        return createMovement(req, MovementType.OTHER_IN, null, MovementStatus.DRAFT);
    }

    @Transactional(rollbackFor = Exception.class)
    public MovementVO createOtherOut(MovementCreateRequest req) {
        return createMovement(req, MovementType.OTHER_OUT, null, MovementStatus.DRAFT);
    }

    @Transactional(rollbackFor = Exception.class)
    public MovementVO update(Long id, MovementCreateRequest req) {
        StockMovement m = findOrThrow(id);
        if (m.getStatus() != MovementStatus.DRAFT
                && m.getStatus() != MovementStatus.REJECTED
                && m.getStatus() != MovementStatus.RESERVE_FAILED) {
            throw new BizException(ErrorCode.ILLEGAL_STATE_TRANSITION, "仅草稿/驳回/预留失败可编辑");
        }
        warehouseAccessService.checkWarehouseAccess(req.getWarehouseId());
        validateItems(req.getItems());
        m.setWarehouseId(req.getWarehouseId());
        m.setToWarehouseId(req.getToWarehouseId());
        m.setPartnerId(req.getPartnerId());
        m.setRemark(req.getRemark());
        m.getItems().clear();
        int totalQty = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (MovementItemRequest ir : req.getItems()) {
            ProductSku sku = skuRepository.findById(ir.getSkuId())
                    .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "SKU 不存在"));
            BigDecimal price = ir.getUnitPrice() != null ? ir.getUnitPrice() : sku.getSalePrice();
            BigDecimal amount = price.multiply(BigDecimal.valueOf(ir.getPlannedQty()));
            m.addItem(StockMovementItem.builder()
                    .skuId(ir.getSkuId())
                    .locationId(ir.getLocationId())
                    .plannedQty(ir.getPlannedQty())
                    .unitPrice(price)
                    .amount(amount)
                    .remark(ir.getRemark())
                    .build());
            totalQty += ir.getPlannedQty();
            totalAmount = totalAmount.add(amount);
        }
        m.setTotalQty(totalQty);
        m.setTotalAmount(totalAmount);
        if (m.getStatus() == MovementStatus.REJECTED || m.getStatus() == MovementStatus.RESERVE_FAILED) {
            m.setStatus(MovementStatus.DRAFT);
        }
        return toVO(movementRepository.save(m));
    }

    @Transactional(readOnly = true)
    public PrintMovementVO print(Long id) {
        StockMovement m = findOrThrow(id);
        String warehouseName = warehouseRepository.findById(m.getWarehouseId())
                .map(Warehouse::getName).orElse("");
        String toName = m.getToWarehouseId() == null ? null
                : warehouseRepository.findById(m.getToWarehouseId()).map(Warehouse::getName).orElse(null);
        String partnerName = null;
        if (m.getPartnerId() != null && m.getPartnerType() == PartnerType.SUPPLIER) {
            partnerName = supplierRepository.findById(m.getPartnerId()).map(Supplier::getName).orElse(null);
        } else if (m.getPartnerId() != null && m.getPartnerType() == PartnerType.CUSTOMER) {
            partnerName = customerRepository.findById(m.getPartnerId()).map(Customer::getName).orElse(null);
        }
        MovementVO vo = toVO(m);
        return PrintMovementVO.builder()
                .movement(vo)
                .warehouseName(warehouseName)
                .toWarehouseName(toName)
                .partnerName(partnerName)
                .printedAt(Instant.now())
                .items(vo.getItems())
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public MovementVO createPurchaseFromAlerts(List<Long> alertIds, Long supplierId) {
        if (alertIds == null || alertIds.isEmpty()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "请选择预警");
        }
        List<StockAlert> alerts = alertRepository.findByIdIn(alertIds);
        if (alerts.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "预警不存在");
        }
        Long warehouseId = alerts.get(0).getWarehouseId();
        List<MovementItemRequest> items = new ArrayList<>();
        for (StockAlert alert : alerts) {
            if (!warehouseId.equals(alert.getWarehouseId())) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "请选择同一仓库的预警");
            }
            int need = Math.max(alert.getThreshold() - alert.getCurrentQty(), 1);
            MovementItemRequest ir = new MovementItemRequest();
            ir.setSkuId(alert.getSkuId());
            ir.setPlannedQty(need);
            items.add(ir);
        }
        MovementCreateRequest req = new MovementCreateRequest();
        req.setWarehouseId(warehouseId);
        req.setPartnerId(supplierId);
        req.setRemark("由低库存预警生成");
        req.setItems(items);
        return createPurchaseIn(req);
    }

    @Transactional(rollbackFor = Exception.class)
    public MovementVO submit(Long id, VersionRequest req) {
        StockMovement m = findOrThrow(id);
        checkVersion(m, req.getVersion());
        if (m.getType() == MovementType.SALE_OUT) {
            return submitSaleOut(m);
        }
        if (m.getStatus() != MovementStatus.DRAFT && m.getStatus() != MovementStatus.REJECTED) {
            throw new BizException(ErrorCode.ILLEGAL_STATE_TRANSITION);
        }
        m.setStatus(MovementStatus.PENDING_APPROVAL);
        m.setSubmittedAt(Instant.now());
        return toVO(movementRepository.save(m));
    }

    @Transactional(rollbackFor = Exception.class)
    public MovementVO approve(Long id, VersionRequest req) {
        StockMovement m = findOrThrow(id);
        checkVersion(m, req.getVersion());
        checkApproverNotCreator(m);
        MovementStatus expected = switch (m.getType()) {
            case SALE_OUT -> MovementStatus.RESERVED;
            default -> MovementStatus.PENDING_APPROVAL;
        };
        if (m.getStatus() != expected) {
            throw new BizException(ErrorCode.ILLEGAL_STATE_TRANSITION);
        }
        m.setStatus(MovementStatus.APPROVED);
        m.setApproverId(currentUserId());
        m.setApprovedAt(Instant.now());
        auditLogService.log("APPROVE_MOVEMENT", "stock_movement", String.valueOf(id), Map.of("no", m.getMovementNo()));
        return toVO(movementRepository.save(m));
    }

    @Transactional(rollbackFor = Exception.class)
    public MovementVO reject(Long id, VersionRequest req) {
        StockMovement m = findOrThrow(id);
        checkVersion(m, req.getVersion());
        checkApproverNotCreator(m);
        if (m.getStatus() != MovementStatus.PENDING_APPROVAL && m.getStatus() != MovementStatus.RESERVED) {
            throw new BizException(ErrorCode.ILLEGAL_STATE_TRANSITION);
        }
        if (m.getType() == MovementType.SALE_OUT && m.getStatus() == MovementStatus.RESERVED) {
            releaseAllReserved(m);
        }
        m.setStatus(MovementStatus.REJECTED);
        m.setRejectReason(req.getReason());
        return toVO(movementRepository.save(m));
    }

    @Transactional(rollbackFor = Exception.class)
    public MovementVO receive(Long id, ExecuteRequest req) {
        StockMovement m = findOrThrow(id);
        checkVersion(m, req.getVersion());
        warehouseAccessService.checkWarehouseAccess(m.getWarehouseId());
        TraceContext.getOrCreate();
        if (m.getType() == MovementType.PURCHASE_IN || m.getType() == MovementType.OTHER_IN) {
            if (m.getStatus() != MovementStatus.APPROVED && m.getStatus() != MovementStatus.RECEIVING) {
                throw new BizException(ErrorCode.ILLEGAL_STATE_TRANSITION);
            }
            m.setStatus(MovementStatus.RECEIVING);
            executeReceive(m, req, false);
            m.setStatus(MovementStatus.COMPLETED);
        } else if (m.getType() == MovementType.SALE_RETURN) {
            if (m.getStatus() != MovementStatus.APPROVED && m.getStatus() != MovementStatus.RECEIVING) {
                throw new BizException(ErrorCode.ILLEGAL_STATE_TRANSITION);
            }
            executeReceive(m, req, true);
            m.setStatus(MovementStatus.COMPLETED);
        } else {
            throw new BizException(ErrorCode.ILLEGAL_STATE_TRANSITION);
        }
        m.setExecutorId(currentUserId());
        m.setExecutedAt(Instant.now());
        MovementVO vo = toVO(movementRepository.save(m));
        triggerAlertScan(m.getWarehouseId());
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public MovementVO pick(Long id, VersionRequest req) {
        StockMovement m = findOrThrow(id);
        checkVersion(m, req.getVersion());
        warehouseAccessService.checkWarehouseAccess(m.getWarehouseId());
        if (m.getType() != MovementType.SALE_OUT || m.getStatus() != MovementStatus.APPROVED) {
            throw new BizException(ErrorCode.ILLEGAL_STATE_TRANSITION);
        }
        m.setStatus(MovementStatus.PICKING);
        return toVO(movementRepository.save(m));
    }

    @Transactional(rollbackFor = Exception.class)
    public MovementVO ship(Long id, ExecuteRequest req) {
        StockMovement m = findOrThrow(id);
        checkVersion(m, req.getVersion());
        warehouseAccessService.checkWarehouseAccess(m.getWarehouseId());
        TraceContext.getOrCreate();
        if (m.getType() == MovementType.SALE_OUT) {
            if (m.getStatus() != MovementStatus.PICKING) {
                throw new BizException(ErrorCode.ILLEGAL_STATE_TRANSITION);
            }
            executeShip(m, req);
            m.setStatus(MovementStatus.SHIPPED);
        } else if (m.getType() == MovementType.PURCHASE_RETURN || m.getType() == MovementType.OTHER_OUT) {
            if (m.getStatus() != MovementStatus.APPROVED) {
                throw new BizException(ErrorCode.ILLEGAL_STATE_TRANSITION);
            }
            executePurchaseReturn(m, req);
            m.setStatus(MovementStatus.COMPLETED);
        } else {
            throw new BizException(ErrorCode.ILLEGAL_STATE_TRANSITION);
        }
        m.setExecutorId(currentUserId());
        m.setExecutedAt(Instant.now());
        MovementVO vo = toVO(movementRepository.save(m));
        triggerAlertScan(m.getWarehouseId());
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public MovementVO transferOut(Long id, ExecuteRequest req) {
        StockMovement m = findOrThrow(id);
        checkVersion(m, req.getVersion());
        warehouseAccessService.checkWarehouseAccess(m.getWarehouseId());
        if (m.getType() != MovementType.TRANSFER || m.getStatus() != MovementStatus.APPROVED) {
            throw new BizException(ErrorCode.ILLEGAL_STATE_TRANSITION);
        }
        Map<Long, ExecuteItemRequest> execMap = req.getItems().stream()
                .collect(Collectors.toMap(ExecuteItemRequest::getItemId, e -> e));
        for (StockMovementItem item : m.getItems()) {
            ExecuteItemRequest ex = execMap.get(item.getId());
            if (ex == null) continue;
            int qty = ex.getActualQty() != null ? ex.getActualQty() : item.getPlannedQty();
            item.setActualQty(qty);
            Long loc = item.getLocationId() != null ? item.getLocationId() : 0L;
            inventoryService.transferOut(item.getSkuId(), m.getWarehouseId(), loc, qty,
                    m.getId(), item.getId(), "调拨调出");
            inventoryService.addInTransit(item.getSkuId(), m.getToWarehouseId(), qty,
                    m.getId(), item.getId(), "调拨在途");
        }
        m.setStatus(MovementStatus.IN_TRANSIT);
        m.setExecutorId(currentUserId());
        m.setExecutedAt(Instant.now());
        triggerAlertScan(m.getWarehouseId());
        return toVO(movementRepository.save(m));
    }

    @Transactional(rollbackFor = Exception.class)
    public MovementVO transferIn(Long id, ExecuteRequest req) {
        StockMovement m = findOrThrow(id);
        checkVersion(m, req.getVersion());
        warehouseAccessService.checkWarehouseAccess(m.getToWarehouseId());
        if (m.getType() != MovementType.TRANSFER || m.getStatus() != MovementStatus.IN_TRANSIT) {
            throw new BizException(ErrorCode.ILLEGAL_STATE_TRANSITION);
        }
        Map<Long, ExecuteItemRequest> execMap = req.getItems().stream()
                .collect(Collectors.toMap(ExecuteItemRequest::getItemId, e -> e));
        for (StockMovementItem item : m.getItems()) {
            ExecuteItemRequest ex = execMap.get(item.getId());
            if (ex == null) continue;
            int qty = ex.getActualQty() != null ? ex.getActualQty() : item.getActualQty();
            Long loc = ex.getLocationId() != null ? ex.getLocationId() : 0L;
            inventoryService.transferIn(item.getSkuId(), m.getToWarehouseId(), loc, qty,
                    m.getId(), item.getId(), "调拨调入");
        }
        m.setStatus(MovementStatus.COMPLETED);
        m.setExecutorId(currentUserId());
        m.setExecutedAt(Instant.now());
        triggerAlertScan(m.getToWarehouseId());
        return toVO(movementRepository.save(m));
    }

    @Transactional(rollbackFor = Exception.class)
    public MovementVO cancel(Long id, VersionRequest req) {
        StockMovement m = findOrThrow(id);
        checkVersion(m, req.getVersion());
        MovementStatus st = m.getStatus();
        if (st == MovementStatus.DRAFT || st == MovementStatus.RESERVE_FAILED
                || st == MovementStatus.REJECTED || st == MovementStatus.PENDING_APPROVAL) {
            m.setStatus(MovementStatus.CANCELLED);
        } else if (st == MovementStatus.RESERVED || st == MovementStatus.PICKING
                || (st == MovementStatus.APPROVED && m.getType() == MovementType.SALE_OUT)) {
            releaseAllReserved(m);
            m.setStatus(MovementStatus.CANCELLED);
        } else if (st == MovementStatus.APPROVED) {
            m.setStatus(MovementStatus.CANCELLED);
        } else if (st == MovementStatus.IN_TRANSIT && m.getType() == MovementType.TRANSFER) {
            rollbackTransfer(m);
            m.setStatus(MovementStatus.CANCELLED);
        } else {
            throw new BizException(ErrorCode.ILLEGAL_STATE_TRANSITION, "当前状态不可取消");
        }
        m.setCancelledAt(Instant.now());
        m.setCancelReason(req.getReason());
        return toVO(movementRepository.save(m));
    }

    public Map<Long, Integer> getAvailable(Long warehouseId, List<Long> skuIds) {
        warehouseAccessService.checkWarehouseAccess(warehouseId);
        List<Inventory> list = inventoryRepository.findByWarehouseIdAndSkuIdIn(warehouseId, skuIds);
        Map<Long, Integer> map = new HashMap<>();
        for (Long skuId : skuIds) {
            map.put(skuId, 0);
        }
        for (Inventory inv : list) {
            map.merge(inv.getSkuId(), inv.getAvailableQty(), Integer::sum);
        }
        return map;
    }

    private MovementVO submitSaleOut(StockMovement m) {
        if (m.getStatus() != MovementStatus.DRAFT && m.getStatus() != MovementStatus.RESERVE_FAILED) {
            throw new BizException(ErrorCode.ILLEGAL_STATE_TRANSITION);
        }
        List<Map<String, Object>> insufficient = new ArrayList<>();
        List<StockMovementItem> sorted = m.getItems().stream()
                .sorted(Comparator.comparing(StockMovementItem::getSkuId))
                .toList();
        try {
            for (StockMovementItem item : sorted) {
                Long loc = item.getLocationId() != null ? item.getLocationId() : 0L;
                Inventory inv = inventoryService.getOrCreate(item.getSkuId(), m.getWarehouseId(), loc);
                if (inv.getAvailableQty() < item.getPlannedQty()) {
                    insufficient.add(Map.of(
                            "skuId", item.getSkuId(),
                            "need", item.getPlannedQty(),
                            "available", inv.getAvailableQty()));
                }
            }
            if (!insufficient.isEmpty()) {
                m.setStatus(MovementStatus.RESERVE_FAILED);
                movementRepository.save(m);
                throw new BizException(ErrorCode.INSUFFICIENT_STOCK, "可用库存不足", insufficient);
            }
            for (StockMovementItem item : sorted) {
                Long loc = item.getLocationId() != null ? item.getLocationId() : 0L;
                inventoryService.reserve(item.getSkuId(), m.getWarehouseId(), loc,
                        item.getPlannedQty(), m.getId(), item.getId(), "销售预留");
            }
            m.setStatus(MovementStatus.RESERVED);
            m.setSubmittedAt(Instant.now());
            return toVO(movementRepository.save(m));
        } catch (BizException e) {
            if (e.getErrorCode() == ErrorCode.INSUFFICIENT_STOCK) {
                throw e;
            }
            m.setStatus(MovementStatus.RESERVE_FAILED);
            movementRepository.save(m);
            throw e;
        }
    }

    private void executeReceive(StockMovement m, ExecuteRequest req, boolean isReturn) {
        Map<Long, ExecuteItemRequest> execMap = req.getItems().stream()
                .collect(Collectors.toMap(ExecuteItemRequest::getItemId, e -> e));
        for (StockMovementItem item : m.getItems()) {
            ExecuteItemRequest ex = execMap.get(item.getId());
            if (ex == null) continue;
            int qty = ex.getActualQty();
            if (qty > item.getPlannedQty()) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "实收数量不能超过计划数量");
            }
            item.setActualQty(qty);
            item.setCond(ex.getCondition());
            Long loc = ex.getLocationId() != null ? ex.getLocationId() : 0L;
            Long targetWarehouseId = m.getWarehouseId();
            if (isReturn && ex.getCondition() == ItemCondition.DEFECTIVE) {
                targetWarehouseId = warehouseRepository.findByTypeAndStatus(WarehouseType.VIRTUAL, 1)
                        .stream().findFirst()
                        .orElseThrow(() -> new BizException(ErrorCode.VALIDATION_ERROR, "请先创建退货待检虚拟仓"))
                        .getId();
            }
            if (isReturn) {
                inventoryService.returnIn(item.getSkuId(), targetWarehouseId, loc, qty,
                        m.getId(), item.getId(),
                        ex.getCondition() == ItemCondition.DEFECTIVE ? "销售退货次品入虚拟仓" : "销售退货入库");
                if (m.getRefMovementId() != null) {
                    updateReturnedQty(m.getRefMovementId(), item.getSkuId(), qty);
                }
            } else {
                inventoryService.increase(item.getSkuId(), m.getWarehouseId(), loc, qty,
                        m.getId(), item.getId(), "采购验货入库");
            }
        }
    }

    private void executeShip(StockMovement m, ExecuteRequest req) {
        Map<Long, ExecuteItemRequest> execMap = req.getItems().stream()
                .collect(Collectors.toMap(ExecuteItemRequest::getItemId, e -> e));
        for (StockMovementItem item : m.getItems()) {
            ExecuteItemRequest ex = execMap.get(item.getId());
            int qty = ex != null && ex.getActualQty() != null ? ex.getActualQty() : item.getPlannedQty();
            if (qty > item.getPlannedQty()) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "出库数量不能超过计划数量");
            }
            item.setActualQty(qty);
            Long loc = item.getLocationId() != null ? item.getLocationId() : 0L;
            inventoryService.deduct(item.getSkuId(), m.getWarehouseId(), loc, qty,
                    m.getId(), item.getId(), "销售出库");
        }
    }

    private void executePurchaseReturn(StockMovement m, ExecuteRequest req) {
        Map<Long, ExecuteItemRequest> execMap = req.getItems().stream()
                .collect(Collectors.toMap(ExecuteItemRequest::getItemId, e -> e));
        for (StockMovementItem item : m.getItems()) {
            ExecuteItemRequest ex = execMap.get(item.getId());
            int qty = ex != null && ex.getActualQty() != null ? ex.getActualQty() : item.getPlannedQty();
            item.setActualQty(qty);
            Long loc = item.getLocationId() != null ? item.getLocationId() : 0L;
            inventoryService.returnOut(item.getSkuId(), m.getWarehouseId(), loc, qty,
                    m.getId(), item.getId(), "采购退货出库");
            if (m.getRefMovementId() != null) {
                updateReturnedQty(m.getRefMovementId(), item.getSkuId(), qty);
            }
        }
    }

    private void releaseAllReserved(StockMovement m) {
        for (StockMovementItem item : m.getItems()) {
            Long loc = item.getLocationId() != null ? item.getLocationId() : 0L;
            inventoryService.release(item.getSkuId(), m.getWarehouseId(), loc,
                    item.getPlannedQty(), m.getId(), item.getId(), "取消释放预留");
        }
    }

    private void rollbackTransfer(StockMovement m) {
        for (StockMovementItem item : m.getItems()) {
            int qty = item.getActualQty() != null ? item.getActualQty() : item.getPlannedQty();
            Long loc = item.getLocationId() != null ? item.getLocationId() : 0L;
            inventoryService.cancelInTransit(item.getSkuId(), m.getToWarehouseId(), qty,
                    m.getId(), item.getId(), "调拨取消回滚在途");
            inventoryService.increase(item.getSkuId(), m.getWarehouseId(), loc, qty,
                    m.getId(), item.getId(), "调拨取消回滚");
        }
    }

    private void updateReturnedQty(Long refMovementId, Long skuId, int qty) {
        StockMovement ref = findOrThrow(refMovementId);
        for (StockMovementItem item : ref.getItems()) {
            if (item.getSkuId().equals(skuId)) {
                item.setReturnedQty(item.getReturnedQty() + qty);
            }
        }
        movementRepository.save(ref);
    }

    private void validateReturnQty(StockMovement ref, List<MovementItemRequest> items) {
        Map<Long, Integer> shippedMap = ref.getItems().stream()
                .collect(Collectors.toMap(StockMovementItem::getSkuId,
                        i -> (i.getActualQty() != null ? i.getActualQty() : 0) - i.getReturnedQty(),
                        Integer::sum));
        for (MovementItemRequest req : items) {
            int canReturn = shippedMap.getOrDefault(req.getSkuId(), 0);
            if (req.getPlannedQty() > canReturn) {
                throw new BizException(ErrorCode.RETURN_EXCEEDS_ORIGINAL);
            }
        }
    }

    private MovementVO createMovement(MovementCreateRequest req, MovementType type,
                                    PartnerType partnerType, MovementStatus status) {
        warehouseAccessService.checkWarehouseAccess(req.getWarehouseId());
        validateItems(req.getItems());
        StockMovement m = StockMovement.builder()
                .movementNo(docSequenceService.nextNo(type.docPrefix()))
                .type(type)
                .status(status)
                .warehouseId(req.getWarehouseId())
                .toWarehouseId(req.getToWarehouseId())
                .partnerType(partnerType)
                .partnerId(req.getPartnerId())
                .refMovementId(req.getRefMovementId())
                .creatorId(currentUserId())
                .remark(req.getRemark())
                .build();
        int totalQty = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (MovementItemRequest ir : req.getItems()) {
            ProductSku sku = skuRepository.findById(ir.getSkuId())
                    .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "SKU 不存在"));
            BigDecimal price = ir.getUnitPrice() != null ? ir.getUnitPrice() : sku.getSalePrice();
            BigDecimal amount = price.multiply(BigDecimal.valueOf(ir.getPlannedQty()));
            StockMovementItem item = StockMovementItem.builder()
                    .skuId(ir.getSkuId())
                    .locationId(ir.getLocationId())
                    .plannedQty(ir.getPlannedQty())
                    .unitPrice(price)
                    .amount(amount)
                    .remark(ir.getRemark())
                    .build();
            m.addItem(item);
            totalQty += ir.getPlannedQty();
            totalAmount = totalAmount.add(amount);
        }
        m.setTotalQty(totalQty);
        m.setTotalAmount(totalAmount);
        return toVO(movementRepository.save(m));
    }

    private void validateItems(List<MovementItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "明细不能为空");
        }
        for (MovementItemRequest item : items) {
            if (item.getSkuId() == null || item.getPlannedQty() == null || item.getPlannedQty() <= 0) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "明细 SKU 和数量无效");
            }
        }
    }

    private void checkVersion(StockMovement m, Integer version) {
        if (version == null || !version.equals(m.getVersion())) {
            throw new BizException(ErrorCode.STATE_CONFLICT);
        }
    }

    private void checkApproverNotCreator(StockMovement m) {
        if (!warehouseAccessService.isAdmin() && m.getCreatorId().equals(currentUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "不能审核自己创建的单据");
        }
    }

    private Long currentUserId() {
        return SecurityUtils.currentUser().getId();
    }

    StockMovement findOrThrow(Long id) {
        return movementRepository.findWithItemsById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "单据不存在"));
    }

    private void triggerAlertScan(Long warehouseId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                alertService.scanWarehouse(warehouseId);
            }
        });
    }

    private Map<Long, List<StockMovementItem>> loadItemsByMovementIds(List<Long> movementIds) {
        if (movementIds == null || movementIds.isEmpty()) {
            return Map.of();
        }
        return movementItemRepository.findByMovementIdInOrderByIdAsc(movementIds).stream()
                .collect(Collectors.groupingBy(item -> item.getMovement().getId()));
    }

    private List<StockMovementItem> resolveItems(StockMovement m) {
        if (m.getItems() != null && Hibernate.isInitialized(m.getItems())) {
            return m.getItems();
        }
        if (m.getId() == null) {
            return m.getItems() != null ? m.getItems() : List.of();
        }
        return movementItemRepository.findByMovementIdOrderByIdAsc(m.getId());
    }

    private MovementVO toVO(StockMovement m) {
        return toVO(m, resolveItems(m));
    }

    private MovementVO toVO(StockMovement m, List<StockMovementItem> itemEntities) {
        List<MovementItemVO> items = itemEntities.stream().map(item -> {
            String skuCode = skuRepository.findById(item.getSkuId())
                    .map(ProductSku::getSkuCode).orElse("");
            return MovementItemVO.builder()
                    .id(item.getId())
                    .skuId(item.getSkuId())
                    .skuCode(skuCode)
                    .locationId(item.getLocationId())
                    .plannedQty(item.getPlannedQty())
                    .actualQty(item.getActualQty())
                    .returnedQty(item.getReturnedQty())
                    .unitPrice(item.getUnitPrice())
                    .amount(item.getAmount())
                    .cond(item.getCond())
                    .remark(item.getRemark())
                    .build();
        }).toList();
        return MovementVO.builder()
                .id(m.getId())
                .movementNo(m.getMovementNo())
                .type(m.getType())
                .status(m.getStatus())
                .warehouseId(m.getWarehouseId())
                .toWarehouseId(m.getToWarehouseId())
                .partnerType(m.getPartnerType())
                .partnerId(m.getPartnerId())
                .refMovementId(m.getRefMovementId())
                .totalQty(m.getTotalQty())
                .totalAmount(m.getTotalAmount())
                .creatorId(m.getCreatorId())
                .approverId(m.getApproverId())
                .executorId(m.getExecutorId())
                .submittedAt(m.getSubmittedAt())
                .approvedAt(m.getApprovedAt())
                .executedAt(m.getExecutedAt())
                .cancelledAt(m.getCancelledAt())
                .cancelReason(m.getCancelReason())
                .rejectReason(m.getRejectReason())
                .remark(m.getRemark())
                .version(m.getVersion())
                .createdAt(m.getCreatedAt())
                .items(items)
                .build();
    }

    private InventoryLogVO toLogVO(InventoryLog log) {
        String skuCode = skuRepository.findById(log.getSkuId())
                .map(ProductSku::getSkuCode).orElse("");
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
                .movementNo(null)
                .operatorId(log.getOperatorId())
                .traceId(log.getTraceId())
                .remark(log.getRemark())
                .operatedAt(log.getOperatedAt())
                .build();
    }
}
