package com.dream.inventory.service;

import com.dream.inventory.common.BizException;
import com.dream.inventory.common.ErrorCode;
import com.dream.inventory.security.LoginUser;
import com.dream.inventory.security.SecurityUtils;
import org.springframework.stereotype.Service;

@Service
public class WarehouseAccessService {

    public void checkWarehouseAccess(Long warehouseId) {
        if (warehouseId == null) return;
        LoginUser user = SecurityUtils.currentUser();
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (user.getRoleCodes().contains("ROLE_ADMIN")) {
            return;
        }
        if (user.getRoleCodes().contains("ROLE_WAREHOUSE")
                && !user.getWarehouseIds().contains(warehouseId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权操作该仓库");
        }
    }

    public boolean isAdmin() {
        LoginUser user = SecurityUtils.currentUser();
        return user != null && user.getRoleCodes().contains("ROLE_ADMIN");
    }
}
