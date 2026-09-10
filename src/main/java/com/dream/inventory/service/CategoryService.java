package com.dream.inventory.service;

import com.dream.inventory.common.BizException;
import com.dream.inventory.common.ErrorCode;
import com.dream.inventory.dto.category.CategoryCreateRequest;
import com.dream.inventory.dto.category.CategoryUpdateRequest;
import com.dream.inventory.dto.category.CategoryVO;
import com.dream.inventory.entity.ProductCategory;
import com.dream.inventory.repository.ProductCategoryRepository;
import com.dream.inventory.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final ProductCategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public List<CategoryVO> tree() {
        List<ProductCategory> all = categoryRepository.findAllByOrderBySortOrderAscIdAsc();
        Map<Long, Integer> productCounts = loadProductCounts();
        Map<Long, CategoryVO> map = new HashMap<>();
        List<CategoryVO> roots = new ArrayList<>();

        for (ProductCategory c : all) {
            map.put(c.getId(), CategoryVO.builder()
                    .id(c.getId())
                    .name(c.getName())
                    .parentId(c.getParentId())
                    .sortOrder(c.getSortOrder())
                    .productCount(productCounts.getOrDefault(c.getId(), 0))
                    .createdAt(c.getCreatedAt())
                    .updatedAt(c.getUpdatedAt())
                    .build());
        }
        for (ProductCategory c : all) {
            CategoryVO vo = map.get(c.getId());
            if (c.getParentId() == null) {
                roots.add(vo);
            } else {
                CategoryVO parent = map.get(c.getParentId());
                if (parent != null) {
                    parent.getChildren().add(vo);
                } else {
                    roots.add(vo);
                }
            }
        }
        for (CategoryVO root : roots) {
            fillAggregates(root);
        }
        return roots;
    }

    private Map<Long, Integer> loadProductCounts() {
        Map<Long, Integer> counts = new HashMap<>();
        for (Object[] row : productRepository.countGroupedByCategoryId()) {
            if (row[0] == null || row[1] == null) {
                continue;
            }
            counts.put(((Number) row[0]).longValue(), ((Number) row[1]).intValue());
        }
        return counts;
    }

    private int fillAggregates(CategoryVO node) {
        List<CategoryVO> children = node.getChildren() == null ? List.of() : node.getChildren();
        node.setChildCount(children.size());
        int total = node.getProductCount() == null ? 0 : node.getProductCount();
        for (CategoryVO child : children) {
            total += fillAggregates(child);
        }
        node.setTotalProductCount(total);
        return total;
    }

    @Transactional(rollbackFor = Exception.class)
    public CategoryVO create(CategoryCreateRequest request) {
        if (request.getParentId() != null) {
            categoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "父分类不存在"));
        }
        ProductCategory category = ProductCategory.builder()
                .name(request.getName().trim())
                .parentId(request.getParentId())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .build();
        category = categoryRepository.save(category);
        return toVO(category);
    }

    @Transactional(rollbackFor = Exception.class)
    public CategoryVO update(Long id, CategoryUpdateRequest request) {
        ProductCategory category = findOrThrow(id);
        category.setName(request.getName().trim());
        if (request.getSortOrder() != null) {
            category.setSortOrder(request.getSortOrder());
        }
        return toVO(categoryRepository.save(category));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ProductCategory category = findOrThrow(id);
        if (categoryRepository.existsByParentId(id)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "存在子分类，无法删除");
        }
        if (productRepository.existsByCategoryId(id)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "分类下存在商品，无法删除");
        }
        categoryRepository.delete(category);
    }

    private ProductCategory findOrThrow(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "分类不存在"));
    }

    private CategoryVO toVO(ProductCategory category) {
        int productCount = (int) productRepository.countByCategoryId(category.getId());
        int childCount = (int) categoryRepository.countByParentId(category.getId());
        return CategoryVO.builder()
                .id(category.getId())
                .name(category.getName())
                .parentId(category.getParentId())
                .sortOrder(category.getSortOrder())
                .productCount(productCount)
                .totalProductCount(productCount)
                .childCount(childCount)
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }
}
