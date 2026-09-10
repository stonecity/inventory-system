package com.dream.inventory.service;

import com.dream.inventory.common.BizException;
import com.dream.inventory.common.ErrorCode;
import com.dream.inventory.common.PageResult;
import com.dream.inventory.dto.partner.PartnerCreateRequest;
import com.dream.inventory.dto.partner.PartnerUpdateRequest;
import com.dream.inventory.dto.partner.PartnerVO;
import com.dream.inventory.entity.Customer;
import com.dream.inventory.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    public PageResult<PartnerVO> list(String keyword, Integer status, int page, int size) {
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        Page<Customer> result = customerRepository.search(kw, status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        return PageResult.of(result.getContent().stream().map(this::toVO).toList(),
                result.getTotalElements(), page, size);
    }

    @Transactional(rollbackFor = Exception.class)
    public PartnerVO create(PartnerCreateRequest req) {
        if (customerRepository.existsByCode(req.getCode())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "客户编码已存在");
        }
        Customer c = Customer.builder()
                .code(req.getCode().trim())
                .name(req.getName().trim())
                .contactPerson(req.getContactPerson())
                .phone(req.getPhone())
                .address(req.getAddress())
                .remark(req.getRemark())
                .status(1)
                .build();
        return toVO(customerRepository.save(c));
    }

    @Transactional(rollbackFor = Exception.class)
    public PartnerVO update(Long id, PartnerUpdateRequest req) {
        Customer c = findOrThrow(id);
        c.setName(req.getName().trim());
        c.setContactPerson(req.getContactPerson());
        c.setPhone(req.getPhone());
        c.setAddress(req.getAddress());
        c.setRemark(req.getRemark());
        return toVO(customerRepository.save(c));
    }

    @Transactional(rollbackFor = Exception.class)
    public PartnerVO updateStatus(Long id, Integer status) {
        Customer c = findOrThrow(id);
        c.setStatus(status);
        return toVO(customerRepository.save(c));
    }

    private Customer findOrThrow(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "客户不存在"));
    }

    private PartnerVO toVO(Customer c) {
        return PartnerVO.builder()
                .id(c.getId()).code(c.getCode()).name(c.getName())
                .contactPerson(c.getContactPerson()).phone(c.getPhone())
                .address(c.getAddress()).status(c.getStatus()).remark(c.getRemark())
                .createdAt(c.getCreatedAt()).build();
    }
}
