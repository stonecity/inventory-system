-- =============================================================================
-- IMS 目标库 DDL  (PRD v1.0 / CONTEXT)
-- 引擎 InnoDB / 字符集 utf8mb4 / 时区约定 Asia/Shanghai (UTC+8)
-- MySQL 8.0.16+（CHECK 约束生效）
--
-- 使用：
--   mysql -u root -p < docs/schema.sql
-- 生产不要依赖 jpa.hibernate.ddl-auto=update，以本文件（或 Flyway 等价迁移）为准。
-- 旧基线 product.stock / stock_in / stock_out 已废弃，禁止再在其上加功能。
-- =============================================================================

CREATE DATABASE IF NOT EXISTS inventory_system
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE inventory_system;

SET NAMES utf8mb4;
SET time_zone = '+08:00';

-- ---------------------------------------------------------------------------
-- 0. 清理（仅开发环境可整库重建时使用；生产请注释掉）
-- ---------------------------------------------------------------------------
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS sys_idempotency_key;
DROP TABLE IF EXISTS sys_doc_sequence;
DROP TABLE IF EXISTS sys_setting;
DROP TABLE IF EXISTS sys_audit_log;
DROP TABLE IF EXISTS stock_alert;
DROP TABLE IF EXISTS safety_stock_rule;
DROP TABLE IF EXISTS inventory_log;
DROP TABLE IF EXISTS inventory;
DROP TABLE IF EXISTS stocktake_item;
DROP TABLE IF EXISTS stocktake;
DROP TABLE IF EXISTS stock_movement_item;
DROP TABLE IF EXISTS stock_movement;
DROP TABLE IF EXISTS customer;
DROP TABLE IF EXISTS supplier;
DROP TABLE IF EXISTS sys_user_warehouse;
DROP TABLE IF EXISTS location;
DROP TABLE IF EXISTS warehouse;
DROP TABLE IF EXISTS product_sku;
DROP TABLE IF EXISTS product;
DROP TABLE IF EXISTS product_category;
DROP TABLE IF EXISTS sys_role_permission;
DROP TABLE IF EXISTS sys_user_role;
DROP TABLE IF EXISTS sys_permission;
DROP TABLE IF EXISTS sys_role;
DROP TABLE IF EXISTS sys_user;
SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- 1. 认证与权限 (RBAC)
-- =============================================================================

CREATE TABLE sys_user (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  username        VARCHAR(50)  NOT NULL,
  password_hash   VARCHAR(100) NOT NULL COMMENT 'BCrypt',
  real_name       VARCHAR(50)  NOT NULL,
  phone           VARCHAR(20)  NULL,
  email           VARCHAR(100) NULL,
  status          TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
  last_login_at   TIMESTAMP    NULL,
  created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_user_username (username)
) ENGINE=InnoDB COMMENT='用户';

CREATE TABLE sys_role (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  code        VARCHAR(50) NOT NULL COMMENT 'ROLE_ADMIN / ROLE_WAREHOUSE / ROLE_BIZ',
  name        VARCHAR(50) NOT NULL,
  description VARCHAR(200) NULL,
  created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_role_code (code)
) ENGINE=InnoDB COMMENT='角色';

CREATE TABLE sys_permission (
  id        BIGINT      NOT NULL AUTO_INCREMENT,
  code      VARCHAR(80) NOT NULL COMMENT '形如 purchase:approve / inventory:unlock',
  name      VARCHAR(80) NOT NULL,
  module    VARCHAR(50) NOT NULL COMMENT '权限树分组',
  parent_id BIGINT      NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_permission_code (code),
  KEY idx_sys_permission_parent (parent_id),
  CONSTRAINT fk_sys_permission_parent
    FOREIGN KEY (parent_id) REFERENCES sys_permission (id)
) ENGINE=InnoDB COMMENT='权限点';

CREATE TABLE sys_user_role (
  user_id    BIGINT    NOT NULL,
  role_id    BIGINT    NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, role_id),
  KEY idx_sys_user_role_role (role_id),
  CONSTRAINT fk_sys_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE CASCADE,
  CONSTRAINT fk_sys_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role (id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='用户-角色 N:M';

CREATE TABLE sys_role_permission (
  role_id       BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  PRIMARY KEY (role_id, permission_id),
  KEY idx_sys_role_permission_perm (permission_id),
  CONSTRAINT fk_sys_role_permission_role FOREIGN KEY (role_id) REFERENCES sys_role (id) ON DELETE CASCADE,
  CONSTRAINT fk_sys_role_permission_perm FOREIGN KEY (permission_id) REFERENCES sys_permission (id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='角色-权限 N:M';

-- =============================================================================
-- 2. 基础数据：分类 / SPU / SKU / 仓库 / 库位 / 往来单位
-- =============================================================================

CREATE TABLE product_category (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  name       VARCHAR(50) NOT NULL,
  parent_id  BIGINT      NULL,
  sort_order INT         NOT NULL DEFAULT 0,
  created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_product_category_parent (parent_id),
  CONSTRAINT fk_product_category_parent
    FOREIGN KEY (parent_id) REFERENCES product_category (id)
) ENGINE=InnoDB COMMENT='商品分类';

CREATE TABLE product (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  name        VARCHAR(100) NOT NULL,
  category_id BIGINT       NULL,
  brand       VARCHAR(50)  NULL,
  unit        VARCHAR(10)  NOT NULL DEFAULT '件',
  status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
  remark      VARCHAR(255) NULL,
  created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_product_category (category_id),
  KEY idx_product_name (name),
  CONSTRAINT fk_product_category
    FOREIGN KEY (category_id) REFERENCES product_category (id)
) ENGINE=InnoDB COMMENT='SPU 商品抽象';

CREATE TABLE product_sku (
  id                    BIGINT        NOT NULL AUTO_INCREMENT,
  spu_id                BIGINT        NOT NULL,
  sku_code              VARCHAR(50)   NOT NULL COMMENT '业务编码 / 扫码主键',
  barcode               VARCHAR(64)   NULL,
  spec_json             JSON          NULL COMMENT '{"color":"红","size":"XL"}',
  cost_price            DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  sale_price            DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  default_safety_stock  INT           NOT NULL DEFAULT 0 COMMENT '全局默认安全库存，可被仓库级规则覆盖',
  status                TINYINT       NOT NULL DEFAULT 1,
  version               INT           NOT NULL DEFAULT 0 COMMENT '乐观锁',
  created_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_product_sku_code (sku_code),
  UNIQUE KEY uk_product_sku_barcode (barcode),
  KEY idx_product_sku_spu (spu_id),
  CONSTRAINT fk_product_sku_spu FOREIGN KEY (spu_id) REFERENCES product (id),
  CONSTRAINT chk_product_sku_price CHECK (cost_price >= 0 AND sale_price >= 0),
  CONSTRAINT chk_product_sku_safety CHECK (default_safety_stock >= 0)
) ENGINE=InnoDB COMMENT='SKU 最小库存单元；库存/单据/日志全部挂 SKU';

CREATE TABLE warehouse (
  id              BIGINT      NOT NULL AUTO_INCREMENT,
  code            VARCHAR(30) NOT NULL,
  name            VARCHAR(50) NOT NULL,
  type            VARCHAR(20) NOT NULL DEFAULT 'PHYSICAL' COMMENT 'PHYSICAL 实体仓 / VIRTUAL 虚拟仓（在途、退货待检）',
  address         VARCHAR(200) NULL,
  manager_user_id BIGINT      NULL,
  status          TINYINT     NOT NULL DEFAULT 1,
  created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_warehouse_code (code),
  KEY idx_warehouse_manager (manager_user_id),
  CONSTRAINT fk_warehouse_manager FOREIGN KEY (manager_user_id) REFERENCES sys_user (id),
  CONSTRAINT chk_warehouse_type CHECK (type IN ('PHYSICAL', 'VIRTUAL'))
) ENGINE=InnoDB COMMENT='仓库';

-- 库位。无库位管理时使用哨兵：id 不强制为 0，库存行 location_id = 0 表示未指定库位。
-- location_id=0 不建外键（避免每个仓都插 id=0）；应用层把 0 视为「未指定」。
CREATE TABLE location (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  warehouse_id BIGINT      NOT NULL,
  code         VARCHAR(30) NOT NULL COMMENT '仓内编码，如 A-01-03',
  zone         VARCHAR(20) NULL,
  shelf        VARCHAR(20) NULL,
  is_default   TINYINT     NOT NULL DEFAULT 0 COMMENT '1 该仓默认库位',
  status       TINYINT     NOT NULL DEFAULT 1,
  created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_location_wh_code (warehouse_id, code),
  CONSTRAINT fk_location_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse (id)
) ENGINE=InnoDB COMMENT='库位';

CREATE TABLE sys_user_warehouse (
  user_id      BIGINT    NOT NULL,
  warehouse_id BIGINT    NOT NULL,
  created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, warehouse_id),
  KEY idx_sys_user_warehouse_wh (warehouse_id),
  CONSTRAINT fk_sys_user_warehouse_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE CASCADE,
  CONSTRAINT fk_sys_user_warehouse_wh FOREIGN KEY (warehouse_id) REFERENCES warehouse (id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='用户所辖仓库数据范围 N:M';

CREATE TABLE supplier (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  code           VARCHAR(30)  NOT NULL,
  name           VARCHAR(100) NOT NULL,
  contact_person VARCHAR(50)  NULL,
  phone          VARCHAR(20)  NULL,
  address        VARCHAR(200) NULL,
  status         TINYINT      NOT NULL DEFAULT 1,
  remark         VARCHAR(255) NULL,
  created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_supplier_code (code)
) ENGINE=InnoDB COMMENT='供应商';

CREATE TABLE customer (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  code           VARCHAR(30)  NOT NULL,
  name           VARCHAR(100) NOT NULL,
  contact_person VARCHAR(50)  NULL,
  phone          VARCHAR(20)  NULL,
  address        VARCHAR(200) NULL,
  status         TINYINT      NOT NULL DEFAULT 1,
  remark         VARCHAR(255) NULL,
  created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_customer_code (code)
) ENGINE=InnoDB COMMENT='客户';

-- =============================================================================
-- 3. 出入库单据（统一主表 + 明细）
-- =============================================================================

CREATE TABLE stock_movement (
  id              BIGINT         NOT NULL AUTO_INCREMENT,
  movement_no     VARCHAR(32)    NOT NULL COMMENT '{类型前缀}{yyyyMMdd}{6位序列} 如 PI20260910000012',
  type            VARCHAR(20)    NOT NULL COMMENT 'PURCHASE_IN/SALE_OUT/SALE_RETURN/PURCHASE_RETURN/TRANSFER/ADJUST/OTHER_IN/OTHER_OUT',
  status          VARCHAR(32)    NOT NULL DEFAULT 'DRAFT' COMMENT '见 PRD §4 状态机；调拨在途用 IN_TRANSIT',
  warehouse_id    BIGINT         NOT NULL COMMENT '源仓；入库类为目标仓',
  to_warehouse_id BIGINT         NULL COMMENT '调拨目标仓',
  partner_type    VARCHAR(20)    NULL COMMENT 'SUPPLIER / CUSTOMER',
  partner_id      BIGINT         NULL COMMENT '多态：供应商或客户 id，由 partner_type 解释',
  ref_movement_id BIGINT         NULL COMMENT '退货单关联原单',
  ref_stocktake_id BIGINT        NULL COMMENT 'ADJUST 关联盘点单',
  total_qty       INT            NOT NULL DEFAULT 0,
  total_amount    DECIMAL(14,2)  NOT NULL DEFAULT 0.00,
  creator_id      BIGINT         NOT NULL,
  approver_id     BIGINT         NULL,
  executor_id     BIGINT         NULL,
  submitted_at    TIMESTAMP      NULL,
  approved_at     TIMESTAMP      NULL,
  executed_at     TIMESTAMP      NULL,
  cancelled_at    TIMESTAMP      NULL,
  cancel_reason   VARCHAR(255)   NULL,
  reject_reason   VARCHAR(255)   NULL,
  remark          VARCHAR(255)   NULL,
  version         INT            NOT NULL DEFAULT 0 COMMENT '乐观锁：防重复审核/执行',
  created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_stock_movement_no (movement_no),
  KEY idx_sm_type_status_created (type, status, created_at),
  KEY idx_sm_warehouse_type_status (warehouse_id, type, status),
  KEY idx_sm_to_warehouse (to_warehouse_id),
  KEY idx_sm_partner (partner_type, partner_id),
  KEY idx_sm_ref_movement (ref_movement_id),
  KEY idx_sm_ref_stocktake (ref_stocktake_id),
  KEY idx_sm_creator (creator_id),
  KEY idx_sm_status_submitted (status, submitted_at),
  CONSTRAINT fk_sm_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse (id),
  CONSTRAINT fk_sm_to_warehouse FOREIGN KEY (to_warehouse_id) REFERENCES warehouse (id),
  CONSTRAINT fk_sm_ref_movement FOREIGN KEY (ref_movement_id) REFERENCES stock_movement (id),
  CONSTRAINT fk_sm_creator FOREIGN KEY (creator_id) REFERENCES sys_user (id),
  CONSTRAINT fk_sm_approver FOREIGN KEY (approver_id) REFERENCES sys_user (id),
  CONSTRAINT fk_sm_executor FOREIGN KEY (executor_id) REFERENCES sys_user (id),
  CONSTRAINT chk_sm_type CHECK (type IN (
    'PURCHASE_IN','SALE_OUT','SALE_RETURN','PURCHASE_RETURN',
    'TRANSFER','ADJUST','OTHER_IN','OTHER_OUT'
  )),
  CONSTRAINT chk_sm_status CHECK (status IN (
    'DRAFT','PENDING_APPROVAL','APPROVED','REJECTED',
    'RESERVED','RESERVE_FAILED','RECEIVING','PICKING','IN_TRANSIT',
    'SHIPPED','COMPLETED','PARTIALLY_COMPLETED','CANCELLED'
  )),
  CONSTRAINT chk_sm_partner_type CHECK (partner_type IS NULL OR partner_type IN ('SUPPLIER','CUSTOMER')),
  CONSTRAINT chk_sm_qty CHECK (total_qty >= 0 AND total_amount >= 0),
  CONSTRAINT chk_sm_transfer_wh CHECK (to_warehouse_id IS NULL OR to_warehouse_id <> warehouse_id)
) ENGINE=InnoDB COMMENT='出入库/调拨/调整单据主表';

CREATE TABLE stock_movement_item (
  id           BIGINT        NOT NULL AUTO_INCREMENT,
  movement_id  BIGINT        NOT NULL,
  sku_id       BIGINT        NOT NULL,
  location_id  BIGINT        NULL COMMENT 'NULL 或 0 表示未指定库位',
  planned_qty  INT           NOT NULL COMMENT '计划数量',
  actual_qty   INT           NULL COMMENT '实收/实发，执行前 NULL',
  returned_qty INT           NOT NULL DEFAULT 0 COMMENT '本行已被退货累计，防超退',
  unit_price   DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  amount       DECIMAL(14,2) NOT NULL DEFAULT 0.00,
  cond         VARCHAR(20)   NULL COMMENT 'GOOD 良品 / DEFECTIVE 次品（退货验货）',
  batch_no     VARCHAR(50)   NULL,
  expire_date  DATE          NULL,
  remark       VARCHAR(255)  NULL,
  created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_smi_movement (movement_id),
  KEY idx_smi_sku (sku_id),
  KEY idx_smi_location (location_id),
  CONSTRAINT fk_smi_movement FOREIGN KEY (movement_id) REFERENCES stock_movement (id) ON DELETE CASCADE,
  CONSTRAINT fk_smi_sku FOREIGN KEY (sku_id) REFERENCES product_sku (id),
  CONSTRAINT chk_smi_planned CHECK (planned_qty > 0),
  CONSTRAINT chk_smi_actual CHECK (actual_qty IS NULL OR actual_qty >= 0),
  CONSTRAINT chk_smi_returned CHECK (returned_qty >= 0),
  CONSTRAINT chk_smi_price CHECK (unit_price >= 0 AND amount >= 0),
  CONSTRAINT chk_smi_cond CHECK (cond IS NULL OR cond IN ('GOOD','DEFECTIVE'))
) ENGINE=InnoDB COMMENT='单据明细';

-- =============================================================================
-- 4. 盘点
-- =============================================================================

CREATE TABLE stocktake (
  id                 BIGINT        NOT NULL AUTO_INCREMENT,
  stocktake_no       VARCHAR(32)   NOT NULL,
  warehouse_id       BIGINT        NOT NULL,
  scope              VARCHAR(20)   NOT NULL COMMENT 'ALL / BY_CATEGORY / BY_LOCATION / BY_SKU',
  scope_value        JSON          NULL COMMENT '范围 id 列表',
  status             VARCHAR(20)   NOT NULL DEFAULT 'CREATED' COMMENT 'CREATED/LOCKED/COUNTING/PENDING_APPROVAL/COMPLETED/CANCELLED',
  snapshot_at        TIMESTAMP     NULL,
  creator_id         BIGINT        NOT NULL,
  approver_id        BIGINT        NULL,
  adjust_movement_id BIGINT        NULL COMMENT '1:0..1 生成的 ADJUST 单据',
  gain_qty           INT           NOT NULL DEFAULT 0,
  loss_qty           INT           NOT NULL DEFAULT 0,
  gain_amount        DECIMAL(14,2) NOT NULL DEFAULT 0.00,
  loss_amount        DECIMAL(14,2) NOT NULL DEFAULT 0.00,
  submitted_at       TIMESTAMP     NULL,
  approved_at        TIMESTAMP     NULL,
  cancelled_at       TIMESTAMP     NULL,
  reject_reason      VARCHAR(255)  NULL,
  remark             VARCHAR(255)  NULL,
  version            INT           NOT NULL DEFAULT 0,
  created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_stocktake_no (stocktake_no),
  UNIQUE KEY uk_stocktake_adjust_movement (adjust_movement_id),
  KEY idx_stocktake_wh_status (warehouse_id, status),
  KEY idx_stocktake_creator (creator_id),
  CONSTRAINT fk_stocktake_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse (id),
  CONSTRAINT fk_stocktake_creator FOREIGN KEY (creator_id) REFERENCES sys_user (id),
  CONSTRAINT fk_stocktake_approver FOREIGN KEY (approver_id) REFERENCES sys_user (id),
  CONSTRAINT fk_stocktake_adjust_movement FOREIGN KEY (adjust_movement_id) REFERENCES stock_movement (id),
  CONSTRAINT chk_stocktake_scope CHECK (scope IN ('ALL','BY_CATEGORY','BY_LOCATION','BY_SKU')),
  CONSTRAINT chk_stocktake_status CHECK (status IN (
    'CREATED','LOCKED','COUNTING','PENDING_APPROVAL','COMPLETED','CANCELLED'
  ))
) ENGINE=InnoDB COMMENT='盘点任务';

ALTER TABLE stock_movement
  ADD CONSTRAINT fk_sm_ref_stocktake
    FOREIGN KEY (ref_stocktake_id) REFERENCES stocktake (id);

CREATE TABLE stocktake_item (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  stocktake_id BIGINT      NOT NULL,
  sku_id       BIGINT      NOT NULL,
  location_id  BIGINT      NOT NULL DEFAULT 0 COMMENT '0 = 未指定库位，与 inventory 粒度对齐',
  snapshot_qty INT         NOT NULL COMMENT '冻结时 on_hand_qty；reserved 不参与差异',
  counted_qty  INT         NULL COMMENT '实盘数，未录入 NULL',
  diff_qty     INT         NULL COMMENT 'counted - snapshot',
  diff_reason  VARCHAR(255) NULL,
  excluded     TINYINT     NOT NULL DEFAULT 0 COMMENT '1 被管理员临时解锁并移出本次盘点',
  counted_by   BIGINT      NULL,
  counted_at   TIMESTAMP   NULL,
  created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_stocktake_item (stocktake_id, sku_id, location_id),
  KEY idx_stocktake_item_sku (sku_id),
  CONSTRAINT fk_stocktake_item_st FOREIGN KEY (stocktake_id) REFERENCES stocktake (id) ON DELETE CASCADE,
  CONSTRAINT fk_stocktake_item_sku FOREIGN KEY (sku_id) REFERENCES product_sku (id)
) ENGINE=InnoDB COMMENT='盘点明细';

-- =============================================================================
-- 5. 库存主表 & 变动日志（核心）
-- =============================================================================

CREATE TABLE inventory (
  id                 BIGINT    NOT NULL AUTO_INCREMENT,
  sku_id             BIGINT    NOT NULL,
  warehouse_id       BIGINT    NOT NULL,
  location_id        BIGINT    NOT NULL DEFAULT 0 COMMENT '无库位时为 0（哨兵，无 FK）',
  on_hand_qty        INT       NOT NULL DEFAULT 0 COMMENT '实物在库',
  reserved_qty       INT       NOT NULL DEFAULT 0 COMMENT '已预留未出库',
  available_qty      INT       NOT NULL DEFAULT 0 COMMENT '可售 = on_hand - reserved',
  in_transit_qty     INT       NOT NULL DEFAULT 0 COMMENT '调拨在途，记在目标仓',
  locked             TINYINT   NOT NULL DEFAULT 0 COMMENT '1 盘点锁定',
  lock_stocktake_id  BIGINT    NULL,
  version            INT       NOT NULL DEFAULT 0 COMMENT '乐观锁；JPA @Version',
  created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sku_wh_loc (sku_id, warehouse_id, location_id),
  KEY idx_inventory_wh_sku (warehouse_id, sku_id),
  KEY idx_inventory_lock_st (lock_stocktake_id),
  KEY idx_inventory_wh_available (warehouse_id, available_qty),
  CONSTRAINT fk_inventory_sku FOREIGN KEY (sku_id) REFERENCES product_sku (id),
  CONSTRAINT fk_inventory_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse (id),
  CONSTRAINT fk_inventory_lock_stocktake FOREIGN KEY (lock_stocktake_id) REFERENCES stocktake (id),
  CONSTRAINT chk_inventory_nonneg CHECK (
    on_hand_qty >= 0 AND reserved_qty >= 0 AND available_qty >= 0 AND in_transit_qty >= 0
  ),
  CONSTRAINT chk_inventory_balance CHECK (available_qty = on_hand_qty - reserved_qty),
  CONSTRAINT chk_inventory_locked CHECK (locked IN (0, 1))
) ENGINE=InnoDB COMMENT='库存主表；只允许 InventoryService 原子方法改数量';

-- 只增表：应用禁止 UPDATE/DELETE；生产建议对应用账号仅 GRANT SELECT, INSERT
CREATE TABLE inventory_log (
  id               BIGINT      NOT NULL AUTO_INCREMENT,
  inventory_id     BIGINT      NOT NULL,
  sku_id           BIGINT      NOT NULL COMMENT '冗余，免 JOIN',
  warehouse_id     BIGINT      NOT NULL,
  location_id      BIGINT      NOT NULL DEFAULT 0,
  change_type      VARCHAR(20) NOT NULL COMMENT 'IN/OUT/RESERVE/RELEASE/TRANSFER_OUT/TRANSFER_IN/ADJUST_GAIN/ADJUST_LOSS/RETURN_IN/RETURN_OUT',
  delta_qty        INT         NOT NULL COMMENT '有符号变动量',
  on_hand_before   INT         NOT NULL,
  on_hand_after    INT         NOT NULL,
  reserved_before  INT         NOT NULL,
  reserved_after   INT         NOT NULL,
  available_before INT         NOT NULL,
  available_after  INT         NOT NULL,
  movement_id      BIGINT      NULL,
  movement_item_id BIGINT      NULL,
  stocktake_id     BIGINT      NULL,
  operator_id      BIGINT      NOT NULL,
  trace_id         VARCHAR(64) NOT NULL COMMENT '同一事务内多条日志共享',
  remark           VARCHAR(255) NULL,
  operated_at      TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_ilog_inventory_time (inventory_id, operated_at),
  KEY idx_ilog_sku_wh_time (sku_id, warehouse_id, operated_at),
  KEY idx_ilog_movement (movement_id),
  KEY idx_ilog_movement_item (movement_item_id),
  KEY idx_ilog_stocktake (stocktake_id),
  KEY idx_ilog_trace (trace_id),
  KEY idx_ilog_operated_at (operated_at),
  CONSTRAINT fk_ilog_inventory FOREIGN KEY (inventory_id) REFERENCES inventory (id),
  CONSTRAINT fk_ilog_sku FOREIGN KEY (sku_id) REFERENCES product_sku (id),
  CONSTRAINT fk_ilog_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse (id),
  CONSTRAINT fk_ilog_movement FOREIGN KEY (movement_id) REFERENCES stock_movement (id),
  CONSTRAINT fk_ilog_movement_item FOREIGN KEY (movement_item_id) REFERENCES stock_movement_item (id),
  CONSTRAINT fk_ilog_stocktake FOREIGN KEY (stocktake_id) REFERENCES stocktake (id),
  CONSTRAINT fk_ilog_operator FOREIGN KEY (operator_id) REFERENCES sys_user (id),
  CONSTRAINT chk_ilog_change_type CHECK (change_type IN (
    'IN','OUT','RESERVE','RELEASE',
    'TRANSFER_OUT','TRANSFER_IN',
    'ADJUST_GAIN','ADJUST_LOSS',
    'RETURN_IN','RETURN_OUT'
  ))
) ENGINE=InnoDB COMMENT='库存变动审计日志（只增）';

-- =============================================================================
-- 6. 安全库存与预警
-- =============================================================================

-- warehouse_id = 0 表示全局规则（MySQL UNIQUE 对 NULL 不去重）
CREATE TABLE safety_stock_rule (
  id           BIGINT    NOT NULL AUTO_INCREMENT,
  sku_id       BIGINT    NOT NULL,
  warehouse_id BIGINT    NOT NULL DEFAULT 0 COMMENT '0 = 全局默认',
  min_qty      INT       NOT NULL,
  max_qty      INT       NULL,
  enabled      TINYINT   NOT NULL DEFAULT 1,
  created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_safety_sku_wh (sku_id, warehouse_id),
  KEY idx_safety_warehouse (warehouse_id),
  CONSTRAINT fk_safety_sku FOREIGN KEY (sku_id) REFERENCES product_sku (id),
  CONSTRAINT chk_safety_min CHECK (min_qty >= 0),
  CONSTRAINT chk_safety_max CHECK (max_qty IS NULL OR max_qty >= min_qty),
  CONSTRAINT chk_safety_enabled CHECK (enabled IN (0, 1))
) ENGINE=InnoDB COMMENT='安全库存规则';

CREATE TABLE stock_alert (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  rule_id      BIGINT      NULL,
  sku_id       BIGINT      NOT NULL,
  warehouse_id BIGINT      NOT NULL,
  alert_type   VARCHAR(10) NOT NULL COMMENT 'LOW / ZERO / OVER',
  current_qty  INT         NOT NULL,
  threshold    INT         NOT NULL,
  status       VARCHAR(10) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN / ACKED / CLOSED',
  handled_by   BIGINT      NULL,
  handled_at   TIMESTAMP   NULL,
  created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_alert_status_wh (status, warehouse_id),
  KEY idx_alert_sku_wh_status (sku_id, warehouse_id, status),
  KEY idx_alert_rule (rule_id),
  CONSTRAINT fk_alert_rule FOREIGN KEY (rule_id) REFERENCES safety_stock_rule (id),
  CONSTRAINT fk_alert_sku FOREIGN KEY (sku_id) REFERENCES product_sku (id),
  CONSTRAINT fk_alert_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse (id),
  CONSTRAINT fk_alert_handler FOREIGN KEY (handled_by) REFERENCES sys_user (id),
  CONSTRAINT chk_alert_type CHECK (alert_type IN ('LOW','ZERO','OVER')),
  CONSTRAINT chk_alert_status CHECK (status IN ('OPEN','ACKED','CLOSED'))
) ENGINE=InnoDB COMMENT='库存预警；同一 SKU+仓仅保留一条 OPEN 由 Service 保证';

-- =============================================================================
-- 7. 系统：审计 / 参数 / 单号序列 / 幂等
-- =============================================================================

CREATE TABLE sys_audit_log (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  user_id       BIGINT       NULL,
  action        VARCHAR(50)  NOT NULL COMMENT 'LOGIN / APPROVE_MOVEMENT / UNLOCK_INVENTORY / ...',
  resource_type VARCHAR(50)  NULL,
  resource_id   VARCHAR(64)  NULL,
  detail        JSON         NULL,
  ip            VARCHAR(45)  NULL,
  user_agent    VARCHAR(255) NULL,
  trace_id      VARCHAR(64)  NULL,
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_audit_user_time (user_id, created_at),
  KEY idx_audit_action_time (action, created_at),
  KEY idx_audit_resource (resource_type, resource_id),
  CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB COMMENT='操作审计（只增）';

CREATE TABLE sys_setting (
  setting_key   VARCHAR(50)  NOT NULL,
  setting_value VARCHAR(500) NOT NULL,
  description   VARCHAR(200) NULL,
  updated_by    BIGINT       NULL,
  updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (setting_key)
) ENGINE=InnoDB COMMENT='系统参数';

CREATE TABLE sys_doc_sequence (
  prefix      VARCHAR(8) NOT NULL COMMENT 'PI/SO/SR/PR/TF/AJ/OI/OO/ST',
  biz_date    DATE       NOT NULL COMMENT '业务日 yyyy-MM-dd（北京时间）',
  current_seq INT        NOT NULL DEFAULT 0,
  PRIMARY KEY (prefix, biz_date),
  CONSTRAINT chk_doc_seq CHECK (current_seq >= 0)
) ENGINE=InnoDB COMMENT='单据日序列，生成 movement_no / stocktake_no';

CREATE TABLE sys_idempotency_key (
  idem_key     VARCHAR(64)  NOT NULL,
  request_hash VARCHAR(64)  NULL,
  response_body JSON        NULL,
  created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expire_at    TIMESTAMP    NOT NULL COMMENT '默认 24h',
  PRIMARY KEY (idem_key),
  KEY idx_idem_expire (expire_at)
) ENGINE=InnoDB COMMENT='创建类接口 Idempotency-Key 去重';

-- =============================================================================
-- 8. 种子数据：角色、权限、系统参数
-- =============================================================================

INSERT INTO sys_role (code, name, description) VALUES
  ('ROLE_ADMIN',     '超级管理员',   '系统配置、用户权限、最终审批与强制操作'),
  ('ROLE_WAREHOUSE', '仓库管理员',   '所辖仓库验货/拣货/盘点/调拨执行'),
  ('ROLE_BIZ',       '采购/销售人员', '发起采购/销售单据，维护供应商客户，只读库存');

INSERT INTO sys_permission (id, code, name, module, parent_id) VALUES
  (1,  'system',                 '系统管理',     'system',      NULL),
  (2,  'system:user',            '用户管理',     'system',      1),
  (3,  'system:role',            '角色权限',     'system',      1),
  (4,  'system:warehouse',       '仓库库位配置', 'system',      1),
  (5,  'system:audit',           '审计日志',     'system',      1),
  (6,  'master',                 '基础数据',     'master',      NULL),
  (7,  'sku:write',              'SKU 新增编辑', 'master',      6),
  (8,  'sku:disable',            'SKU 停用',     'master',      6),
  (9,  'safety-stock:write',     '安全库存设置', 'master',      6),
  (10, 'partner:write',          '供应商客户维护','master',     6),
  (11, 'purchase:create',        '新建采购入库', 'purchase',    NULL),
  (12, 'purchase:approve',       '审核采购入库', 'purchase',    NULL),
  (13, 'purchase:receive',       '验货入库',     'purchase',    NULL),
  (14, 'sale:create',            '新建销售出库', 'sale',        NULL),
  (15, 'sale:approve',           '审核销售出库', 'sale',        NULL),
  (16, 'sale:ship',              '拣货确认出库', 'sale',        NULL),
  (17, 'sale:cancel',            '取消销售单',   'sale',        NULL),
  (18, 'return:create',          '发起退货',     'return',      NULL),
  (19, 'return:execute',         '执行退货',     'return',      NULL),
  (20, 'transfer:create',        '新建调拨',     'transfer',    NULL),
  (21, 'transfer:approve',       '审核调拨',     'transfer',    NULL),
  (22, 'transfer:execute',       '调出调入确认', 'transfer',    NULL),
  (23, 'stocktake:create',       '创建盘点',     'stocktake',   NULL),
  (24, 'stocktake:count',        '录入实盘',     'stocktake',   NULL),
  (25, 'stocktake:approve',      '审批盘盈盘亏', 'stocktake',   NULL),
  (26, 'inventory:read',         '库存查询',     'inventory',   NULL),
  (27, 'inventory:log',          '变动日志',     'inventory',   NULL),
  (28, 'inventory:adjust',       '库存调整',     'inventory',   NULL),
  (29, 'inventory:unlock',       '盘点期临时解锁','inventory',  NULL),
  (30, 'alert:read',             '查看预警',     'alert',       NULL),
  (31, 'alert:handle',           '处理预警',     'alert',       NULL);

-- ROLE_ADMIN 拥有全部权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p WHERE r.code = 'ROLE_ADMIN';

-- ROLE_WAREHOUSE
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
  ON p.code IN (
    'purchase:receive','sale:ship','return:execute',
    'transfer:create','transfer:execute',
    'stocktake:create','stocktake:count',
    'inventory:read','inventory:log','alert:read','alert:handle','safety-stock:write'
  )
WHERE r.code = 'ROLE_WAREHOUSE';

-- ROLE_BIZ
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
  ON p.code IN (
    'sku:write','partner:write',
    'purchase:create','sale:create','sale:cancel','return:create',
    'inventory:read','inventory:log','alert:read'
  )
WHERE r.code = 'ROLE_BIZ';

INSERT INTO sys_setting (setting_key, setting_value, description) VALUES
  ('reserve.expire.hours',     '48',  '销售预留超时小时数，超时自动取消并释放'),
  ('stocktake.warn.hours',     '72',  '盘点锁定超时未完成告警小时数'),
  ('stocktake.auto-cancel.days','7',  '盘点超时自动取消并解锁天数'),
  ('doc.seq.width',            '6',   '单号日序列位数');
