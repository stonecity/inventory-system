package com.dream.inventory.service;

import com.dream.inventory.common.BizException;
import com.dream.inventory.common.ErrorCode;
import com.dream.inventory.common.PageResult;
import com.dream.inventory.dto.partner.PartnerCreateRequest;
import com.dream.inventory.dto.partner.PartnerUpdateRequest;
import com.dream.inventory.dto.partner.PartnerVO;
import com.dream.inventory.entity.Supplier;
import com.dream.inventory.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;

    public PageResult<PartnerVO> list(String keyword, Integer status, int page, int size) {
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        Page<Supplier> result = supplierRepository.search(kw, status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        return PageResult.of(result.getContent().stream().map(this::toVO).toList(),
                result.getTotalElements(), page, size);
    }

    @Transactional(rollbackFor = Exception.class)
    public PartnerVO create(PartnerCreateRequest req) {
        if (supplierRepository.existsByCode(req.getCode())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "供应商编码已存在");
        }
        Supplier s = Supplier.builder()
                .code(req.getCode().trim())
                .name(req.getName().trim())
                .contactPerson(req.getContactPerson())
                .phone(req.getPhone())
                .address(req.getAddress())
                .remark(req.getRemark())
                .status(1)
                .build();
        return toVO(supplierRepository.save(s));
    }

    @Transactional(rollbackFor = Exception.class)
    public PartnerVO update(Long id, PartnerUpdateRequest req) {
        Supplier s = findOrThrow(id);
        s.setName(req.getName().trim());
        s.setContactPerson(req.getContactPerson());
        s.setPhone(req.getPhone());
        s.setAddress(req.getAddress());
        s.setRemark(req.getRemark());
        return toVO(supplierRepository.save(s));
    }

    @Transactional(rollbackFor = Exception.class)
    public PartnerVO updateStatus(Long id, Integer status) {
        Supplier s = findOrThrow(id);
        s.setStatus(status);
        return toVO(supplierRepository.save(s));
    }

    private Supplier findOrThrow(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "供应商不存在"));
    }

    private PartnerVO toVO(Supplier s) {
        return PartnerVO.builder()
                .id(s.getId()).code(s.getCode()).name(s.getName())
                .contactPerson(s.getContactPerson()).phone(s.getPhone())
                .address(s.getAddress()).status(s.getStatus()).remark(s.getRemark())
                .createdAt(s.getCreatedAt()).build();
    }
}
