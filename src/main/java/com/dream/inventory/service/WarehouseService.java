package com.dream.inventory.service;

import com.dream.inventory.common.BizException;
import com.dream.inventory.common.ErrorCode;
import com.dream.inventory.common.PageResult;
import com.dream.inventory.dto.warehouse.WarehouseCreateRequest;
import com.dream.inventory.dto.warehouse.WarehouseUpdateRequest;
import com.dream.inventory.dto.warehouse.WarehouseVO;
import com.dream.inventory.entity.Warehouse;
import com.dream.inventory.entity.enums.WarehouseType;
import com.dream.inventory.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;

    public PageResult<WarehouseVO> list(String keyword, WarehouseType type, Integer status, int page, int size) {
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<Warehouse> result = warehouseRepository.search(kw, type, status, pageable);
        return PageResult.of(
                result.getContent().stream().map(this::toVO).toList(),
                result.getTotalElements(),
                page,
                size
        );
    }

    public WarehouseVO getById(Long id) {
        return toVO(findOrThrow(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public WarehouseVO create(WarehouseCreateRequest request) {
        if (warehouseRepository.findByCode(request.getCode().trim()).isPresent()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "仓库编码已存在: " + request.getCode());
        }
        Warehouse warehouse = Warehouse.builder()
                .code(request.getCode().trim())
                .name(request.getName().trim())
                .type(request.getType())
                .address(request.getAddress())
                .managerUserId(request.getManagerUserId())
                .status(1)
                .build();
        try {
            return toVO(warehouseRepository.save(warehouse));
        } catch (DataIntegrityViolationException ex) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "仓库编码已存在");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public WarehouseVO update(Long id, WarehouseUpdateRequest request) {
        Warehouse warehouse = findOrThrow(id);
        warehouse.setName(request.getName().trim());
        warehouse.setType(request.getType());
        warehouse.setAddress(request.getAddress());
        warehouse.setManagerUserId(request.getManagerUserId());
        return toVO(warehouseRepository.save(warehouse));
    }

    @Transactional(rollbackFor = Exception.class)
    public WarehouseVO updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "状态值必须为 0 或 1");
        }
        Warehouse warehouse = findOrThrow(id);
        warehouse.setStatus(status);
        return toVO(warehouseRepository.save(warehouse));
    }

    private Warehouse findOrThrow(Long id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "仓库不存在"));
    }

    private WarehouseVO toVO(Warehouse warehouse) {
        return WarehouseVO.builder()
                .id(warehouse.getId())
                .code(warehouse.getCode())
                .name(warehouse.getName())
                .type(warehouse.getType())
                .address(warehouse.getAddress())
                .managerUserId(warehouse.getManagerUserId())
                .status(warehouse.getStatus())
                .createdAt(warehouse.getCreatedAt())
                .updatedAt(warehouse.getUpdatedAt())
                .build();
    }
}
