package com.dream.inventory.service;

import com.dream.inventory.common.PageResult;
import com.dream.inventory.dto.alert.AlertVO;
import com.dream.inventory.entity.*;
import com.dream.inventory.entity.enums.AlertStatus;
import com.dream.inventory.entity.enums.AlertType;
import com.dream.inventory.repository.*;
import com.dream.inventory.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AlertService {

    private final StockAlertRepository alertRepository;
    private final InventoryRepository inventoryRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final ProductSkuRepository skuRepository;
    private final WarehouseRepository warehouseRepository;

    public PageResult<AlertVO> list(AlertStatus status, Long warehouseId, AlertType alertType, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<StockAlert> result = alertRepository.search(status, warehouseId, alertType, pageable);
        List<StockAlert> alerts = result.getContent();
        Set<Long> skuIds = alerts.stream().map(StockAlert::getSkuId).collect(Collectors.toSet());
        Set<Long> warehouseIds = alerts.stream().map(StockAlert::getWarehouseId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> skuCodes = skuRepository.findAllById(skuIds).stream()
                .collect(Collectors.toMap(ProductSku::getId, ProductSku::getSkuCode));
        Map<Long, String> warehouseNames = warehouseRepository.findAllById(warehouseIds).stream()
                .collect(Collectors.toMap(Warehouse::getId, Warehouse::getName));
        List<AlertVO> vos = alerts.stream()
                .map(a -> toVO(a,
                        skuCodes.getOrDefault(a.getSkuId(), ""),
                        warehouseNames.getOrDefault(a.getWarehouseId(), "")))
                .toList();
        return PageResult.of(vos, result.getTotalElements(), page, size);
    }

    @Transactional(rollbackFor = Exception.class)
    public AlertVO ack(Long id) {
        StockAlert alert = findOrThrow(id);
        alert.setStatus(AlertStatus.ACKED);
        alert.setHandledBy(SecurityUtils.currentUser().getId());
        alert.setHandledAt(Instant.now());
        return toVO(alertRepository.save(alert));
    }

    @Transactional(rollbackFor = Exception.class)
    public AlertVO close(Long id) {
        StockAlert alert = findOrThrow(id);
        alert.setStatus(AlertStatus.CLOSED);
        alert.setHandledBy(SecurityUtils.currentUser().getId());
        alert.setHandledAt(Instant.now());
        return toVO(alertRepository.save(alert));
    }

    @Scheduled(fixedRate = 300_000)
    @Transactional(rollbackFor = Exception.class)
    public void scheduledScan() {
        scanAll();
    }

    @Transactional(rollbackFor = Exception.class)
    public void scanAll() {
        List<Inventory> inventories = inventoryRepository.findAll();
        for (Inventory inv : inventories) {
            checkAndAlert(inv);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void scanWarehouse(Long warehouseId) {
        inventoryRepository.search(warehouseId, null, false, 0, null, null,
                PageRequest.of(0, Integer.MAX_VALUE)).getContent()
                .forEach(this::checkAndAlert);
    }

    private void checkAndAlert(Inventory inv) {
        int minQty = resolveMinQty(inv.getSkuId(), inv.getWarehouseId());
        Integer maxQty = resolveMaxQty(inv.getSkuId(), inv.getWarehouseId());
        int available = inv.getAvailableQty();
        AlertType type = null;
        int threshold = minQty;
        if (available <= 0 && minQty > 0) {
            type = AlertType.ZERO;
            threshold = 0;
        } else if (minQty > 0 && available < minQty) {
            type = AlertType.LOW;
            threshold = minQty;
        } else if (maxQty != null && maxQty > 0 && available > maxQty) {
            type = AlertType.OVER;
            threshold = maxQty;
        }
        var existing = alertRepository.findBySkuIdAndWarehouseIdAndStatus(
                inv.getSkuId(), inv.getWarehouseId(), AlertStatus.OPEN);
        if (type == null) {
            existing.ifPresent(a -> {
                a.setStatus(AlertStatus.CLOSED);
                a.setHandledAt(Instant.now());
                alertRepository.save(a);
            });
            return;
        }
        if (existing.isPresent()) {
            StockAlert a = existing.get();
            a.setCurrentQty(available);
            a.setThreshold(threshold);
            a.setAlertType(type);
            alertRepository.save(a);
        } else {
            alertRepository.save(StockAlert.builder()
                    .skuId(inv.getSkuId())
                    .warehouseId(inv.getWarehouseId())
                    .alertType(type)
                    .currentQty(available)
                    .threshold(threshold)
                    .status(AlertStatus.OPEN)
                    .build());
        }
    }

    private int resolveMinQty(Long skuId, Long warehouseId) {
        return safetyStockRuleRepository.findBySkuIdAndWarehouseId(skuId, warehouseId)
                .filter(r -> r.getEnabled() == 1)
                .map(SafetyStockRule::getMinQty)
                .or(() -> safetyStockRuleRepository.findBySkuIdAndWarehouseId(skuId, 0L)
                        .filter(r -> r.getEnabled() == 1)
                        .map(SafetyStockRule::getMinQty))
                .orElseGet(() -> skuRepository.findById(skuId)
                        .map(ProductSku::getDefaultSafetyStock)
                        .orElse(0));
    }

    private Integer resolveMaxQty(Long skuId, Long warehouseId) {
        return safetyStockRuleRepository.findBySkuIdAndWarehouseId(skuId, warehouseId)
                .filter(r -> r.getEnabled() == 1)
                .map(SafetyStockRule::getMaxQty)
                .or(() -> safetyStockRuleRepository.findBySkuIdAndWarehouseId(skuId, 0L)
                        .filter(r -> r.getEnabled() == 1)
                        .map(SafetyStockRule::getMaxQty))
                .orElse(null);
    }

    public long countOpen() {
        return alertRepository.countByStatus(AlertStatus.OPEN);
    }

    private StockAlert findOrThrow(Long id) {
        return alertRepository.findById(id)
                .orElseThrow(() -> new com.dream.inventory.common.BizException(
                        com.dream.inventory.common.ErrorCode.NOT_FOUND, "预警不存在"));
    }

    private AlertVO toVO(StockAlert a) {
        String skuCode = skuRepository.findById(a.getSkuId()).map(ProductSku::getSkuCode).orElse("");
        String warehouseName = warehouseRepository.findById(a.getWarehouseId()).map(Warehouse::getName).orElse("");
        return toVO(a, skuCode, warehouseName);
    }

    private AlertVO toVO(StockAlert a, String skuCode, String warehouseName) {
        return AlertVO.builder()
                .id(a.getId())
                .skuId(a.getSkuId())
                .skuCode(skuCode)
                .warehouseId(a.getWarehouseId())
                .warehouseName(warehouseName)
                .alertType(a.getAlertType())
                .currentQty(a.getCurrentQty())
                .threshold(a.getThreshold())
                .status(a.getStatus())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
