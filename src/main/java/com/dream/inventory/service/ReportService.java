package com.dream.inventory.service;

import com.dream.inventory.dto.report.MovementSummaryVO;
import com.dream.inventory.dto.report.StockAgeVO;
import com.dream.inventory.entity.Inventory;
import com.dream.inventory.entity.InventoryLog;
import com.dream.inventory.entity.ProductSku;
import com.dream.inventory.entity.Warehouse;
import com.dream.inventory.entity.enums.InventoryChangeType;
import com.dream.inventory.entity.enums.MovementStatus;
import com.dream.inventory.entity.enums.MovementType;
import com.dream.inventory.repository.InventoryLogRepository;
import com.dream.inventory.repository.InventoryRepository;
import com.dream.inventory.repository.ProductSkuRepository;
import com.dream.inventory.repository.StockMovementRepository;
import com.dream.inventory.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final List<InventoryChangeType> INBOUND_TYPES = List.of(
            InventoryChangeType.IN, InventoryChangeType.TRANSFER_IN,
            InventoryChangeType.RETURN_IN, InventoryChangeType.ADJUST_GAIN);

    private final StockMovementRepository movementRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryLogRepository inventoryLogRepository;
    private final ProductSkuRepository skuRepository;
    private final WarehouseRepository warehouseRepository;
    private final WarehouseAccessService warehouseAccessService;

    public List<MovementSummaryVO> movementSummary(Instant from, Instant to, Long warehouseId, String groupBy) {
        if (warehouseId != null) {
            warehouseAccessService.checkWarehouseAccess(warehouseId);
        }
        List<MovementStatus> done = List.of(
                MovementStatus.COMPLETED, MovementStatus.SHIPPED, MovementStatus.PARTIALLY_COMPLETED);
        List<MovementSummaryVO> result = new ArrayList<>();
        for (Object[] row : movementRepository.summarize(from, to, warehouseId, done)) {
            MovementType type = (MovementType) row[0];
            Long whId = (Long) row[1];
            long count = ((Number) row[2]).longValue();
            int qty = ((Number) row[3]).intValue();
            BigDecimal amount = row[4] == null ? BigDecimal.ZERO : new BigDecimal(row[4].toString());
            String warehouseName = warehouseRepository.findById(whId).map(Warehouse::getName).orElse("");
            String key = "warehouse".equalsIgnoreCase(groupBy)
                    ? warehouseName
                    : type.name();
            result.add(MovementSummaryVO.builder()
                    .type(type)
                    .warehouseId(whId)
                    .warehouseName(warehouseName)
                    .groupKey(key)
                    .orderCount(count)
                    .totalQty(qty)
                    .totalAmount(amount)
                    .build());
        }
        return result;
    }

    public List<StockAgeVO> stockAge(Long warehouseId) {
        if (warehouseId != null) {
            warehouseAccessService.checkWarehouseAccess(warehouseId);
        }
        List<Inventory> inventories = warehouseId == null
                ? inventoryRepository.findAll()
                : inventoryRepository.findByWarehouseId(warehouseId);
        Instant now = Instant.now();
        List<StockAgeVO> list = new ArrayList<>();
        for (Inventory inv : inventories) {
            if (inv.getOnHandQty() == null || inv.getOnHandQty() <= 0) {
                continue;
            }
            List<InventoryLog> logs = inventoryLogRepository.findLatestInbound(
                    inv.getSkuId(), inv.getWarehouseId(), INBOUND_TYPES, PageRequest.of(0, 1));
            Instant lastIn = logs.isEmpty() ? inv.getCreatedAt() : logs.get(0).getOperatedAt();
            long days = lastIn == null ? 0 : Duration.between(lastIn, now).toDays();
            list.add(StockAgeVO.builder()
                    .skuId(inv.getSkuId())
                    .skuCode(skuRepository.findById(inv.getSkuId()).map(ProductSku::getSkuCode).orElse(""))
                    .warehouseId(inv.getWarehouseId())
                    .warehouseName(warehouseRepository.findById(inv.getWarehouseId()).map(Warehouse::getName).orElse(""))
                    .onHandQty(inv.getOnHandQty())
                    .lastInboundAt(lastIn)
                    .ageDays(days)
                    .build());
        }
        list.sort((a, b) -> Long.compare(b.getAgeDays(), a.getAgeDays()));
        return list;
    }
}
