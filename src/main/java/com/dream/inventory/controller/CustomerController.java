package com.dream.inventory.controller;

import com.dream.inventory.common.PageResult;
import com.dream.inventory.common.Result;
import com.dream.inventory.dto.partner.PartnerCreateRequest;
import com.dream.inventory.dto.partner.PartnerUpdateRequest;
import com.dream.inventory.dto.partner.PartnerVO;
import com.dream.inventory.dto.product.StatusRequest;
import com.dream.inventory.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Result<PageResult<PartnerVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(customerService.list(keyword, status, page, size));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('partner:write')")
    public Result<PartnerVO> create(@Valid @RequestBody PartnerCreateRequest req) {
        return Result.ok(customerService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('partner:write')")
    public Result<PartnerVO> update(@PathVariable Long id, @Valid @RequestBody PartnerUpdateRequest req) {
        return Result.ok(customerService.update(id, req));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('partner:write')")
    public Result<PartnerVO> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest req) {
        return Result.ok(customerService.updateStatus(id, req.getStatus()));
    }
}
