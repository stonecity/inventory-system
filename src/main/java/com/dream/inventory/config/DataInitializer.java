package com.dream.inventory.config;

import com.dream.inventory.entity.SysPermission;
import com.dream.inventory.entity.SysRole;
import com.dream.inventory.entity.SysUser;
import com.dream.inventory.repository.SysPermissionRepository;
import com.dream.inventory.repository.SysRoleRepository;
import com.dream.inventory.repository.SysUserRepository;
import com.dream.inventory.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final SysPermissionRepository permissionRepository;
    private final SysRoleRepository roleRepository;
    private final SysUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SettingsService settingsService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        seedPermissionsAndRoles();
        seedAdminUser();
        settingsService.seedDefaultsIfEmpty();
    }

    private void seedPermissionsAndRoles() {
        if (permissionRepository.count() > 0) {
            return;
        }

        Map<String, SysPermission> permissions = new LinkedHashMap<>();
        permissions.put("system", perm("system", "系统管理", "system", null));
        permissions.put("system:user", perm("system:user", "用户管理", "system", permissions.get("system").getId()));
        permissions.put("system:role", perm("system:role", "角色权限", "system", permissions.get("system").getId()));
        permissions.put("system:warehouse", perm("system:warehouse", "仓库库位配置", "system", permissions.get("system").getId()));
        permissions.put("system:audit", perm("system:audit", "审计日志", "system", permissions.get("system").getId()));
        permissions.put("master", perm("master", "基础数据", "master", null));
        permissions.put("sku:write", perm("sku:write", "SKU 新增编辑", "master", permissions.get("master").getId()));
        permissions.put("sku:disable", perm("sku:disable", "SKU 停用", "master", permissions.get("master").getId()));
        permissions.put("safety-stock:write", perm("safety-stock:write", "安全库存设置", "master", permissions.get("master").getId()));
        permissions.put("partner:write", perm("partner:write", "供应商客户维护", "master", permissions.get("master").getId()));
        permissions.put("purchase:create", perm("purchase:create", "新建采购入库", "purchase", null));
        permissions.put("purchase:approve", perm("purchase:approve", "审核采购入库", "purchase", null));
        permissions.put("purchase:receive", perm("purchase:receive", "验货入库", "purchase", null));
        permissions.put("sale:create", perm("sale:create", "新建销售出库", "sale", null));
        permissions.put("sale:approve", perm("sale:approve", "审核销售出库", "sale", null));
        permissions.put("sale:ship", perm("sale:ship", "拣货确认出库", "sale", null));
        permissions.put("sale:cancel", perm("sale:cancel", "取消销售单", "sale", null));
        permissions.put("return:create", perm("return:create", "发起退货", "return", null));
        permissions.put("return:execute", perm("return:execute", "执行退货", "return", null));
        permissions.put("transfer:create", perm("transfer:create", "新建调拨", "transfer", null));
        permissions.put("transfer:approve", perm("transfer:approve", "审核调拨", "transfer", null));
        permissions.put("transfer:execute", perm("transfer:execute", "调出调入确认", "transfer", null));
        permissions.put("stocktake:create", perm("stocktake:create", "创建盘点", "stocktake", null));
        permissions.put("stocktake:count", perm("stocktake:count", "录入实盘", "stocktake", null));
        permissions.put("stocktake:approve", perm("stocktake:approve", "审批盘盈盘亏", "stocktake", null));
        permissions.put("inventory:read", perm("inventory:read", "库存查询", "inventory", null));
        permissions.put("inventory:log", perm("inventory:log", "变动日志", "inventory", null));
        permissions.put("inventory:adjust", perm("inventory:adjust", "库存调整", "inventory", null));
        permissions.put("inventory:unlock", perm("inventory:unlock", "盘点期临时解锁", "inventory", null));
        permissions.put("alert:read", perm("alert:read", "查看预警", "alert", null));
        permissions.put("alert:handle", perm("alert:handle", "处理预警", "alert", null));

        // Fix parent_id after first save pass
        for (SysPermission p : permissionRepository.findAll()) {
            permissions.put(p.getCode(), p);
        }
        updateParent(permissions, "system:user", "system");
        updateParent(permissions, "system:role", "system");
        updateParent(permissions, "system:warehouse", "system");
        updateParent(permissions, "system:audit", "system");
        updateParent(permissions, "sku:write", "master");
        updateParent(permissions, "sku:disable", "master");
        updateParent(permissions, "safety-stock:write", "master");
        updateParent(permissions, "partner:write", "master");
        permissionRepository.saveAll(permissions.values());

        SysRole admin = role("ROLE_ADMIN", "超级管理员", "系统配置、用户权限、最终审批与强制操作");
        admin.getPermissions().addAll(permissions.values());
        roleRepository.save(admin);

        SysRole warehouse = role("ROLE_WAREHOUSE", "仓库管理员", "所辖仓库验货/拣货/盘点/调拨执行");
        addPerm(warehouse, permissions, "purchase:receive", "sale:ship", "return:execute",
                "transfer:create", "transfer:execute", "stocktake:create", "stocktake:count",
                "inventory:read", "inventory:log", "alert:read", "alert:handle", "safety-stock:write");
        roleRepository.save(warehouse);

        SysRole biz = role("ROLE_BIZ", "采购/销售人员", "发起采购/销售单据，维护供应商客户，只读库存");
        addPerm(biz, permissions, "sku:write", "partner:write", "purchase:create", "sale:create",
                "sale:cancel", "return:create", "inventory:read", "inventory:log", "alert:read");
        roleRepository.save(biz);
    }

    private SysPermission perm(String code, String name, String module, Long parentId) {
        return permissionRepository.save(SysPermission.builder()
                .code(code)
                .name(name)
                .module(module)
                .parentId(parentId)
                .build());
    }

    private void updateParent(Map<String, SysPermission> map, String childCode, String parentCode) {
        SysPermission child = map.get(childCode);
        SysPermission parent = map.get(parentCode);
        if (child != null && parent != null && child.getParentId() == null) {
            child.setParentId(parent.getId());
        }
    }

    private SysRole role(String code, String name, String description) {
        return SysRole.builder().code(code).name(name).description(description).build();
    }

    private void addPerm(SysRole role, Map<String, SysPermission> map, String... codes) {
        for (String code : codes) {
            SysPermission p = map.get(code);
            if (p != null) {
                role.getPermissions().add(p);
            }
        }
    }

    private void seedAdminUser() {
        if (userRepository.existsByUsername("admin")) {
            return;
        }
        SysRole adminRole = roleRepository.findByCode("ROLE_ADMIN")
                .orElseThrow(() -> new IllegalStateException("ROLE_ADMIN 未初始化，请先执行 schema.sql 或清空权限表"));
        SysUser admin = SysUser.builder()
                .username("admin")
                .passwordHash(passwordEncoder.encode("admin123"))
                .realName("系统管理员")
                .status(1)
                .roles(Set.of(adminRole))
                .build();
        userRepository.save(admin);
    }
}
