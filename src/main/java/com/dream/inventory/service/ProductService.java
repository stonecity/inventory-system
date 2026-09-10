package com.dream.inventory.service;

import com.dream.inventory.common.BizException;
import com.dream.inventory.common.ErrorCode;
import com.dream.inventory.common.PageResult;
import com.dream.inventory.dto.product.ProductCreateRequest;
import com.dream.inventory.dto.product.ProductUpdateRequest;
import com.dream.inventory.dto.product.ProductVO;
import com.dream.inventory.entity.Product;
import com.dream.inventory.entity.ProductCategory;
import com.dream.inventory.repository.ProductCategoryRepository;
import com.dream.inventory.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;

    public PageResult<ProductVO> list(String keyword, Integer status, Long categoryId,
                                      boolean includeDescendants, int page, int size) {
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        boolean filterByCategory = categoryId != null;
        Collection<Long> categoryIds = filterByCategory
                ? resolveCategoryIds(categoryId, includeDescendants)
                : List.of(-1L);
        Page<Product> result = productRepository.search(kw, status, filterByCategory ? 1 : 0, categoryIds, pageable);
        return PageResult.of(
                result.getContent().stream().map(this::toVO).toList(),
                result.getTotalElements(),
                page,
                size
        );
    }

    private List<Long> resolveCategoryIds(Long categoryId, boolean includeDescendants) {
        if (!includeDescendants) {
            return List.of(categoryId);
        }
        List<ProductCategory> all = categoryRepository.findAll();
        Map<Long, List<Long>> children = new HashMap<>();
        for (ProductCategory c : all) {
            if (c.getParentId() != null) {
                children.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c.getId());
            }
        }
        List<Long> ids = new ArrayList<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(categoryId);
        while (!queue.isEmpty()) {
            Long id = queue.poll();
            ids.add(id);
            List<Long> kids = children.get(id);
            if (kids != null) {
                queue.addAll(kids);
            }
        }
        return ids.isEmpty() ? List.of(-1L) : ids;
    }

    public ProductVO getById(Long id) {
        return toVO(findOrThrow(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public ProductVO create(ProductCreateRequest request) {
        Product product = Product.builder()
                .name(request.getName().trim())
                .categoryId(request.getCategoryId())
                .brand(request.getBrand())
                .unit(StringUtils.hasText(request.getUnit()) ? request.getUnit() : "件")
                .remark(request.getRemark())
                .status(1)
                .build();
        return toVO(productRepository.save(product));
    }

    @Transactional(rollbackFor = Exception.class)
    public ProductVO update(Long id, ProductUpdateRequest request) {
        Product product = findOrThrow(id);
        product.setName(request.getName().trim());
        product.setCategoryId(request.getCategoryId());
        product.setBrand(request.getBrand());
        if (StringUtils.hasText(request.getUnit())) {
            product.setUnit(request.getUnit());
        }
        product.setRemark(request.getRemark());
        return toVO(productRepository.save(product));
    }

    @Transactional(rollbackFor = Exception.class)
    public ProductVO updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "状态值必须为 0 或 1");
        }
        Product product = findOrThrow(id);
        product.setStatus(status);
        return toVO(productRepository.save(product));
    }

    Product findOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "商品不存在"));
    }

    private ProductVO toVO(Product product) {
        return ProductVO.builder()
                .id(product.getId())
                .name(product.getName())
                .categoryId(product.getCategoryId())
                .brand(product.getBrand())
                .unit(product.getUnit())
                .status(product.getStatus())
                .remark(product.getRemark())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
