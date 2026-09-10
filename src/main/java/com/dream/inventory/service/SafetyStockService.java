package com.dream.inventory.service;

import com.dream.inventory.common.BizException;
import com.dream.inventory.common.ErrorCode;
import com.dream.inventory.common.PageResult;
import com.dream.inventory.dto.safety.SafetyStockCreateRequest;
import com.dream.inventory.dto.safety.SafetyStockVO;
import com.dream.inventory.entity.SafetyStockRule;
import com.dream.inventory.repository.SafetyStockRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SafetyStockService {

    private final SafetyStockRuleRepository ruleRepository;

    public PageResult<SafetyStockVO> list(int page, int size) {
        Page<SafetyStockRule> result = ruleRepository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        return PageResult.of(result.getContent().stream().map(this::toVO).toList(),
                result.getTotalElements(), page, size);
    }

    @Transactional(rollbackFor = Exception.class)
    public SafetyStockVO create(SafetyStockCreateRequest req) {
        Long whId = req.getWarehouseId() != null ? req.getWarehouseId() : 0L;
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
        return toVO(ruleRepository.save(rule));
    }

    @Transactional(rollbackFor = Exception.class)
    public SafetyStockVO update(Long id, SafetyStockCreateRequest req) {
        SafetyStockRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        rule.setMinQty(req.getMinQty());
        rule.setMaxQty(req.getMaxQty());
        if (req.getEnabled() != null) rule.setEnabled(req.getEnabled());
        return toVO(ruleRepository.save(rule));
    }

    private SafetyStockVO toVO(SafetyStockRule r) {
        return SafetyStockVO.builder()
                .id(r.getId()).skuId(r.getSkuId()).warehouseId(r.getWarehouseId())
                .minQty(r.getMinQty()).maxQty(r.getMaxQty()).enabled(r.getEnabled()).build();
    }
}
