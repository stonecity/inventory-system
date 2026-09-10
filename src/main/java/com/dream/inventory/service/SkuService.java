package com.dream.inventory.service;

import com.dream.inventory.common.BizException;
import com.dream.inventory.common.ErrorCode;
import com.dream.inventory.common.PageResult;
import com.dream.inventory.dto.sku.SkuCreateRequest;
import com.dream.inventory.dto.sku.SkuUpdateRequest;
import com.dream.inventory.dto.sku.SkuVO;
import com.dream.inventory.entity.Product;
import com.dream.inventory.entity.ProductSku;
import com.dream.inventory.repository.ProductSkuRepository;
import com.dream.inventory.security.LoginUser;
import com.dream.inventory.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SkuService {

    private final ProductSkuRepository skuRepository;
    private final ProductService productService;

    public PageResult<SkuVO> list(String keyword, Long spuId, Integer status, int page, int size) {
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<ProductSku> result = skuRepository.search(kw, spuId, status, pageable);
        Map<Long, String> spuNames = loadSpuNames(result.getContent());
        return PageResult.of(
                result.getContent().stream().map(s -> toVO(s, spuNames.get(s.getSpuId()))).toList(),
                result.getTotalElements(),
                page,
                size
        );
    }

    public List<SkuVO> listBySpuId(Long spuId) {
        Product spu = productService.findOrThrow(spuId);
        return skuRepository.findBySpuId(spuId).stream()
                .map(s -> toVO(s, spu.getName()))
                .toList();
    }

    public SkuVO getById(Long id) {
        ProductSku sku = findOrThrow(id);
        Product spu = productService.findOrThrow(sku.getSpuId());
        return toVO(sku, spu.getName());
    }

    @Transactional(rollbackFor = Exception.class)
    public SkuVO create(Long spuId, SkuCreateRequest request) {
        Product spu = productService.findOrThrow(spuId);
        validateUniqueCodes(null, request.getSkuCode(), request.getBarcode());

        ProductSku sku = ProductSku.builder()
                .spuId(spuId)
                .skuCode(request.getSkuCode().trim())
                .barcode(blankToNull(request.getBarcode()))
                .specJson(request.getSpecJson())
                .costPrice(defaultDecimal(request.getCostPrice()))
                .salePrice(defaultDecimal(request.getSalePrice()))
                .defaultSafetyStock(request.getDefaultSafetyStock() != null ? request.getDefaultSafetyStock() : 0)
                .status(1)
                .build();
        try {
            return toVO(skuRepository.save(sku), spu.getName());
        } catch (DataIntegrityViolationException ex) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "SKU 编码或条码已存在");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public SkuVO update(Long id, SkuUpdateRequest request) {
        ProductSku sku = findOrThrow(id);
        if (!sku.getVersion().equals(request.getVersion())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "数据已被修改，请刷新后重试");
        }
        validateUniqueCodes(id, request.getSkuCode(), request.getBarcode());

        sku.setSkuCode(request.getSkuCode().trim());
        sku.setBarcode(blankToNull(request.getBarcode()));
        sku.setSpecJson(request.getSpecJson());
        sku.setCostPrice(defaultDecimal(request.getCostPrice()));
        sku.setSalePrice(defaultDecimal(request.getSalePrice()));
        sku.setDefaultSafetyStock(request.getDefaultSafetyStock() != null ? request.getDefaultSafetyStock() : 0);

        try {
            Product spu = productService.findOrThrow(sku.getSpuId());
            return toVO(skuRepository.save(sku), spu.getName());
        } catch (DataIntegrityViolationException ex) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "SKU 编码或条码已存在");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public SkuVO updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "状态值必须为 0 或 1");
        }
        LoginUser current = SecurityUtils.currentUser();
        if (status == 0 && (current == null || !current.getPermissionCodes().contains("sku:disable"))) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权停用 SKU");
        }
        ProductSku sku = findOrThrow(id);
        sku.setStatus(status);
        Product spu = productService.findOrThrow(sku.getSpuId());
        return toVO(skuRepository.save(sku), spu.getName());
    }

    private ProductSku findOrThrow(Long id) {
        return skuRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "SKU 不存在"));
    }

    private void validateUniqueCodes(Long id, String skuCode, String barcode) {
        if (skuRepository.existsBySkuCodeAndIdNot(skuCode.trim(), id != null ? id : -1L)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "SKU 编码已存在: " + skuCode);
        }
        String bc = blankToNull(barcode);
        if (bc != null && skuRepository.existsByBarcodeAndIdNot(bc, id != null ? id : -1L)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "条码已存在: " + bc);
        }
    }

    private Map<Long, String> loadSpuNames(List<ProductSku> skus) {
        return skus.stream()
                .map(ProductSku::getSpuId)
                .distinct()
                .collect(Collectors.toMap(
                        spuId -> spuId,
                        spuId -> productService.findOrThrow(spuId).getName()
                ));
    }

    private SkuVO toVO(ProductSku sku, String spuName) {
        return SkuVO.builder()
                .id(sku.getId())
                .spuId(sku.getSpuId())
                .spuName(spuName)
                .skuCode(sku.getSkuCode())
                .barcode(sku.getBarcode())
                .specJson(sku.getSpecJson())
                .costPrice(sku.getCostPrice())
                .salePrice(sku.getSalePrice())
                .defaultSafetyStock(sku.getDefaultSafetyStock())
                .status(sku.getStatus())
                .version(sku.getVersion())
                .createdAt(sku.getCreatedAt())
                .updatedAt(sku.getUpdatedAt())
                .build();
    }

    private BigDecimal defaultDecimal(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
