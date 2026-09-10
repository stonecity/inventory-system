package com.dream.inventory.service;

import com.dream.inventory.dto.dashboard.DashboardSummaryVO;
import com.dream.inventory.dto.dashboard.TopSkuVO;
import com.dream.inventory.dto.dashboard.TrendPointVO;
import com.dream.inventory.entity.enums.InventoryChangeType;
import com.dream.inventory.entity.enums.MovementStatus;
import com.dream.inventory.repository.InventoryLogRepository;
import com.dream.inventory.repository.InventoryRepository;
import com.dream.inventory.repository.ProductSkuRepository;
import com.dream.inventory.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final Set<String> INBOUND = Set.of(
            InventoryChangeType.IN.name(), InventoryChangeType.TRANSFER_IN.name(),
            InventoryChangeType.ADJUST_GAIN.name(), InventoryChangeType.RETURN_IN.name(),
            InventoryChangeType.RELEASE.name());
    private static final Set<String> OUTBOUND = Set.of(
            InventoryChangeType.OUT.name(), InventoryChangeType.TRANSFER_OUT.name(),
            InventoryChangeType.ADJUST_LOSS.name(), InventoryChangeType.RETURN_OUT.name(),
            InventoryChangeType.RESERVE.name());

    private final ProductSkuRepository skuRepository;
    private final StockMovementRepository movementRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryLogRepository inventoryLogRepository;
    private final AlertService alertService;

    public DashboardSummaryVO summary() {
        BigDecimal amount = inventoryRepository.sumOnHandAmount();
        return DashboardSummaryVO.builder()
                .skuCount(skuRepository.count())
                .inventoryAmount(amount != null ? amount : BigDecimal.ZERO)
                .pendingApprovalCount(movementRepository.countByStatus(MovementStatus.PENDING_APPROVAL)
                        + movementRepository.countByStatus(MovementStatus.RESERVED))
                .openAlertCount(alertService.countOpen())
                .build();
    }

    public List<TrendPointVO> trend(int days) {
        int d = days <= 0 ? 30 : Math.min(days, 90);
        Instant from = Instant.now().minus(d, ChronoUnit.DAYS);
        Map<LocalDate, TrendPointVO> map = new LinkedHashMap<>();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (int i = d - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            map.put(date, TrendPointVO.builder().date(date.toString()).inboundQty(0).outboundQty(0).build());
        }
        for (Object[] row : inventoryLogRepository.trendSince(from)) {
            LocalDate date = toLocalDate(row[0]);
            if (date == null || !map.containsKey(date)) {
                continue;
            }
            String type = String.valueOf(row[1]);
            int qty = ((Number) row[2]).intValue();
            TrendPointVO point = map.get(date);
            if (INBOUND.contains(type)) {
                point.setInboundQty(point.getInboundQty() + qty);
            } else if (OUTBOUND.contains(type)) {
                point.setOutboundQty(point.getOutboundQty() + qty);
            }
        }
        return new ArrayList<>(map.values());
    }

    public List<TopSkuVO> topSkus(int limit) {
        int n = limit <= 0 ? 10 : Math.min(limit, 50);
        List<TopSkuVO> list = new ArrayList<>();
        for (Object[] row : inventoryRepository.topSkusByAmount(n)) {
            list.add(TopSkuVO.builder()
                    .skuId(((Number) row[0]).longValue())
                    .skuCode(String.valueOf(row[1]))
                    .onHandQty(((Number) row[2]).intValue())
                    .amount(row[3] == null ? BigDecimal.ZERO : new BigDecimal(row[3].toString()))
                    .build());
        }
        return list;
    }

    private LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime().toLocalDate();
        }
        if (value instanceof LocalDate ld) {
            return ld;
        }
        return LocalDate.parse(value.toString().substring(0, 10));
    }
}
