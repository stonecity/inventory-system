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
        Map<Long, CategoryVO> map = new HashMap<>();
        List<CategoryVO> roots = new ArrayList<>();

        for (ProductCategory c : all) {
            map.put(c.getId(), CategoryVO.builder()
                    .id(c.getId())
                    .name(c.getName())
                    .parentId(c.getParentId())
                    .sortOrder(c.getSortOrder())
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
        return roots;
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
        return CategoryVO.builder()
                .id(category.getId())
                .name(category.getName())
                .parentId(category.getParentId())
                .sortOrder(category.getSortOrder())
                .build();
    }
}
