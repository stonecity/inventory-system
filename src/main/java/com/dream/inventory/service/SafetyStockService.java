package com.dream.inventory.service;

import com.dream.inventory.common.BizException;
import com.dream.inventory.common.ErrorCode;
import com.dream.inventory.common.PageResult;
import com.dream.inventory.dto.safety.SafetyStockCreateRequest;
import com.dream.inventory.dto.safety.SafetyStockVO;
import com.dream.inventory.entity.Product;
import com.dream.inventory.entity.ProductSku;
import com.dream.inventory.entity.SafetyStockRule;
import com.dream.inventory.entity.Warehouse;
import com.dream.inventory.repository.ProductRepository;
import com.dream.inventory.repository.ProductSkuRepository;
import com.dream.inventory.repository.SafetyStockRuleRepository;
import com.dream.inventory.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SafetyStockService {

    private final SafetyStockRuleRepository ruleRepository;
    private final ProductSkuRepository skuRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;

    public PageResult<SafetyStockVO> list(String keyword, Long warehouseId, Integer enabled, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        List<Long> skuIds = null;
        if (StringUtils.hasText(keyword)) {
            skuIds = skuRepository.search(keyword.trim(), null, null, PageRequest.of(0, 500))
                    .getContent().stream().map(ProductSku::getId).toList();
            if (skuIds.isEmpty()) {
                return PageResult.of(List.of(), 0, page, size);
            }
        }
        boolean filterBySku = skuIds != null;
        Page<SafetyStockRule> result = ruleRepository.search(
                warehouseId, enabled, filterBySku, filterBySku ? skuIds : List.of(-1L), pageable);
        return PageResult.of(toVOs(result.getContent()), result.getTotalElements(), page, size);
    }

    @Transactional(rollbackFor = Exception.class)
    public SafetyStockVO create(SafetyStockCreateRequest req) {
        validateQty(req);
        Long whId = req.getWarehouseId() != null ? req.getWarehouseId() : 0L;
        if (!skuRepository.existsById(req.getSkuId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "SKU 不存在");
        }
        if (whId > 0 && !warehouseRepository.existsById(whId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "仓库不存在");
        }
        if (ruleRepository.findBySkuIdAndWarehouseId(req.getSkuId(), whId).isPresent()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "该 SKU+仓库规则已存在");
        }
        SafetyStockRule rule = SafetyStockRule.builder()
                .skuId(req.getSkuId())
                .warehouseId(whId)
                .minQty(req.getMinQty())
                .maxQty(req.getMaxQty())
                .enabled(req.getEnabled() != null ? req.getEnabled() : 1)
                .build();
        return toVOs(List.of(ruleRepository.save(rule))).get(0);
    }

    @Transactional(rollbackFor = Exception.class)
    public SafetyStockVO update(Long id, SafetyStockCreateRequest req) {
        validateQty(req);
        SafetyStockRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        rule.setMinQty(req.getMinQty());
        rule.setMaxQty(req.getMaxQty());
        if (req.getEnabled() != null) rule.setEnabled(req.getEnabled());
        return toVOs(List.of(ruleRepository.save(rule))).get(0);
    }

    @Transactional(rollbackFor = Exception.class)
    public int importRules(List<SafetyStockCreateRequest> items) {
        int n = 0;
        for (SafetyStockCreateRequest item : items) {
            Long whId = item.getWarehouseId() != null ? item.getWarehouseId() : 0L;
            var existing = ruleRepository.findBySkuIdAndWarehouseId(item.getSkuId(), whId);
            if (existing.isPresent()) {
                update(existing.get().getId(), item);
            } else {
                create(item);
            }
            n++;
        }
        return n;
    }

    private void validateQty(SafetyStockCreateRequest req) {
        if (req.getMaxQty() != null && req.getMaxQty() < req.getMinQty()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "上限不能低于下限");
        }
    }

    private List<SafetyStockVO> toVOs(List<SafetyStockRule> rules) {
        Set<Long> skuIds = rules.stream().map(SafetyStockRule::getSkuId).collect(Collectors.toSet());
        Set<Long> warehouseIds = rules.stream()
                .map(SafetyStockRule::getWarehouseId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        Map<Long, ProductSku> skus = skuRepository.findAllById(skuIds).stream()
                .collect(Collectors.toMap(ProductSku::getId, s -> s));
        Set<Long> spuIds = skus.values().stream().map(ProductSku::getSpuId).collect(Collectors.toSet());
        Map<Long, String> spuNames = productRepository.findAllById(spuIds).stream()
                .collect(Collectors.toMap(Product::getId, Product::getName));
        Map<Long, String> warehouseNames = warehouseIds.isEmpty()
                ? Map.of()
                : warehouseRepository.findAllById(warehouseIds).stream()
                .collect(Collectors.toMap(Warehouse::getId, Warehouse::getName));
        return rules.stream().map(r -> toVO(r, skus.get(r.getSkuId()), spuNames, warehouseNames)).toList();
    }

    private SafetyStockVO toVO(SafetyStockRule r, ProductSku sku, Map<Long, String> spuNames,
                               Map<Long, String> warehouseNames) {
        Long warehouseId = r.getWarehouseId();
        String warehouseName = (warehouseId == null || warehouseId == 0)
                ? "全局"
                : warehouseNames.getOrDefault(warehouseId, "-");
        return SafetyStockVO.builder()
                .id(r.getId())
                .skuId(r.getSkuId())
                .skuCode(sku != null ? sku.getSkuCode() : "")
                .spuName(sku != null ? spuNames.getOrDefault(sku.getSpuId(), "") : "")
                .specJson(sku != null ? sku.getSpecJson() : null)
                .warehouseId(warehouseId)
                .warehouseName(warehouseName)
                .minQty(r.getMinQty())
                .maxQty(r.getMaxQty())
                .enabled(r.getEnabled())
                .build();
    }
}