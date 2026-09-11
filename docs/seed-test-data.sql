-- =============================================================================
-- IMS 测试数据 (对齐 docs/schema.sql / PRD v1.0)
-- =============================================================================
-- 前置：先执行 docs/schema.sql（已含角色、权限、系统参数种子）。
-- 本文件只插入业务测试数据，不重复写入 sys_role / sys_permission / sys_setting。
--
--   mysql -u root -p inventory_system < docs/seed-test-data.sql
--
-- 测试账号（密码均为 123456，BCrypt）：
--   admin         超级管理员
--   wh_sh         上海仓管
--   wh_gz         广州仓管
--   biz_purchase  采购
--   biz_sale      销售
--   disabled      停用账号（不可登录场景）
--
-- 覆盖场景：
--   采购：DRAFT / PENDING_APPROVAL / APPROVED / REJECTED / RECEIVING /
--         COMPLETED / PARTIALLY_COMPLETED / CANCELLED
--   销售：DRAFT / RESERVE_FAILED / RESERVED / APPROVED / PICKING /
--         SHIPPED / CANCELLED（含预留后释放）
--   退货：销售退货良品入原仓、次品入待检仓；采购退货出库
--   调拨：已完成、在途、已审核未执行、草稿
--   盘点：已完成并生成 ADJUST、盘点中锁定、已取消、仅创建
--   预警：LOW / ZERO / OVER / ACKED / CLOSED
-- 库存不变量：available_qty = on_hand_qty - reserved_qty，且三者 >= 0
-- =============================================================================

USE inventory_system;

SET NAMES utf8mb4;
SET time_zone = '+00:00';
SET FOREIGN_KEY_CHECKS = 0;

-- 可重复导入：清掉业务数据，保留 schema 种子（角色/权限/参数）
DELETE FROM sys_idempotency_key;
DELETE FROM sys_doc_sequence;
DELETE FROM sys_audit_log;
DELETE FROM stock_alert;
DELETE FROM safety_stock_rule;
DELETE FROM inventory_log;
DELETE FROM inventory;
DELETE FROM stocktake_item;
DELETE FROM stock_movement_item;
UPDATE stocktake SET adjust_movement_id = NULL;
UPDATE stock_movement SET ref_stocktake_id = NULL, ref_movement_id = NULL;
DELETE FROM stock_movement;
DELETE FROM stocktake;
DELETE FROM customer;
DELETE FROM supplier;
DELETE FROM sys_user_warehouse;
DELETE FROM location;
DELETE FROM warehouse;
DELETE FROM product_sku;
DELETE FROM product;
DELETE FROM product_category;
DELETE FROM sys_user_role;
DELETE FROM sys_user;

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- 1. 用户 / 角色绑定
-- password_hash = BCrypt("123456")
-- =============================================================================

INSERT INTO sys_user
  (id, username, password_hash, real_name, phone, email, status, last_login_at, created_at)
VALUES
  (1, 'admin',         '$2a$10$4yDnehqoHHOz6MB7Wsc..O9y6uzq1avPg7DJI11vy5yvUminRiGmi', '系统管理员', '13800000001', 'admin@ims.local',         1, '2026-09-11 01:00:00', '2026-08-01 00:00:00'),
  (2, 'wh_sh',         '$2a$10$4yDnehqoHHOz6MB7Wsc..O9y6uzq1avPg7DJI11vy5yvUminRiGmi', '张仓管',     '13800000002', 'wh.sh@ims.local',         1, '2026-09-11 00:30:00', '2026-08-01 00:00:00'),
  (3, 'wh_gz',         '$2a$10$4yDnehqoHHOz6MB7Wsc..O9y6uzq1avPg7DJI11vy5yvUminRiGmi', '李仓管',     '13800000003', 'wh.gz@ims.local',         1, '2026-09-10 08:00:00', '2026-08-01 00:00:00'),
  (4, 'biz_purchase',  '$2a$10$4yDnehqoHHOz6MB7Wsc..O9y6uzq1avPg7DJI11vy5yvUminRiGmi', '王采购',     '13800000004', 'purchase@ims.local',      1, '2026-09-10 02:00:00', '2026-08-01 00:00:00'),
  (5, 'biz_sale',      '$2a$10$4yDnehqoHHOz6MB7Wsc..O9y6uzq1avPg7DJI11vy5yvUminRiGmi', '赵销售',     '13800000005', 'sale@ims.local',          1, '2026-09-11 00:10:00', '2026-08-01 00:00:00'),
  (6, 'disabled',      '$2a$10$4yDnehqoHHOz6MB7Wsc..O9y6uzq1avPg7DJI11vy5yvUminRiGmi', '停用账号',   '13800000006', 'disabled@ims.local',      0, NULL,                    '2026-08-15 00:00:00');

INSERT INTO sys_user_role (user_id, role_id) VALUES
  (1, 1),  -- admin          -> ROLE_ADMIN
  (2, 2),  -- wh_sh          -> ROLE_WAREHOUSE
  (3, 2),  -- wh_gz          -> ROLE_WAREHOUSE
  (4, 3),  -- biz_purchase   -> ROLE_BIZ
  (5, 3),  -- biz_sale       -> ROLE_BIZ
  (6, 3);  -- disabled       -> ROLE_BIZ（已停用）

-- =============================================================================
-- 2. 分类 / SPU / SKU
-- =============================================================================

INSERT INTO product_category (id, name, parent_id, sort_order) VALUES
  (1, '家居用品', NULL, 10),
  (2, '收纳整理', 1,    11),
  (3, '清洁护理', 1,    12),
  (4, '办公文具', NULL, 20),
  (5, '书写用品', 4,    21),
  (6, '运动户外', NULL, 30);

INSERT INTO product (id, name, category_id, brand, unit, status, remark) VALUES
  (1, '真空收纳袋',   2, '清居',  '只', 1, '搬家换季主力款'),
  (2, '浓缩洗衣液',   3, '清居',  '瓶', 1, NULL),
  (3, '按动中性笔',   5, '墨点',  '支', 1, NULL),
  (4, 'TPE 瑜伽垫',   6, '跃动',  '张', 1, NULL),
  (5, '乳清蛋白粉',   6, '跃动',  '罐', 1, '需关注效期'),
  (6, '旧款档案袋',   4, '墨点',  '个', 0, '已停用，仅历史单据引用');

INSERT INTO product_sku
  (id, spu_id, sku_code, barcode, spec_json, cost_price, sale_price, default_safety_stock, status, version)
VALUES
  (1,  1, 'BAG-S',       '6902345000018', '{"size":"S","capacity":"40L"}',     15.00,  29.90, 20, 1, 0),
  (2,  1, 'BAG-L',       '6902345000025', '{"size":"L","capacity":"80L"}',     22.00,  39.90, 15, 1, 0),
  (3,  1, 'BAG-XL',      '6902345000032', '{"size":"XL","capacity":"120L"}',   28.00,  59.00, 30, 1, 0),
  (4,  2, 'DET-1L',      '6902345000049', '{"volume":"1L","scent":"清香"}',    18.00,  32.00, 10, 1, 0),
  (5,  2, 'DET-2L',      '6902345000056', '{"volume":"2L","scent":"清香"}',    32.00,  55.00, 10, 1, 0),
  (6,  3, 'PEN-BLK',     '6902345000063', '{"color":"黑","tip":"0.5mm"}',       1.20,   3.50, 50, 1, 0),
  (7,  3, 'PEN-BLU',     '6902345000070', '{"color":"蓝","tip":"0.5mm"}',       1.20,   3.50, 40, 1, 0),
  (8,  4, 'YOGA-6',      '6902345000087', '{"thickness":"6mm","color":"灰"}',  45.00,  99.00,  8, 1, 0),
  (9,  4, 'YOGA-8',      '6902345000094', '{"thickness":"8mm","color":"蓝"}',  58.00, 129.00,  8, 1, 0),
  (10, 5, 'PROT-CHO',    '6902345000100', '{"flavor":"巧克力","net":"2kg"}',   48.00,  89.00, 40, 1, 0),
  (11, 6, 'FOLDER-OLD',  '6902345000117', '{"size":"A4","color":"牛皮"}',       4.50,   0.00,  0, 0, 0);

-- =============================================================================
-- 3. 仓库 / 库位 / 用户数据范围
-- =============================================================================

INSERT INTO warehouse (id, code, name, type, address, manager_user_id, status) VALUES
  (1, 'WH-SH',      '上海总仓',     'PHYSICAL', '上海市浦东新区张江路88号',   2, 1),
  (2, 'WH-GZ',      '广州分仓',     'PHYSICAL', '广州市海珠区新港东路200号', 3, 1),
  (3, 'WH-TRANSIT', '调拨在途仓',   'VIRTUAL',  NULL,                         1, 1),
  (4, 'WH-QC',      '退货待检仓',   'VIRTUAL',  NULL,                         1, 1);

INSERT INTO location (id, warehouse_id, code, zone, shelf, is_default, status) VALUES
  (1, 1, 'A-01-01', 'A', '01', 1, 1),
  (2, 1, 'A-01-02', 'A', '01', 0, 1),
  (3, 1, 'B-02-01', 'B', '02', 0, 1),
  (4, 2, 'A-01-01', 'A', '01', 1, 1),
  (5, 2, 'C-03-01', 'C', '03', 0, 1);

INSERT INTO sys_user_warehouse (user_id, warehouse_id) VALUES
  (1, 1), (1, 2), (1, 3), (1, 4),
  (2, 1),
  (3, 2);

-- =============================================================================
-- 4. 供应商 / 客户
-- =============================================================================

INSERT INTO supplier (id, code, name, contact_person, phone, address, status, remark) VALUES
  (1, 'SUP-JN',  '家宁家居用品',       '周经理', '0512-58880001', '江苏省苏州市相城区家居大道12号', 1, '收纳/清洁主供'),
  (2, 'SUP-WZ',  '文致文具制造',       '吴工',   '0574-26660002', '浙江省宁波市鄞州区文具园8号',     1, '书写用品'),
  (3, 'SUP-YD',  '跃动体育科技',       '陈经理', '020-37770003', '广东省广州市番禺区体育路66号',     1, NULL),
  (4, 'SUP-OLD', '停用供应商（示例）', '无',     NULL,            NULL,                             0, '已停止合作');

INSERT INTO customer (id, code, name, contact_person, phone, address, status, remark) VALUES
  (1, 'CUS-PDD', '拼多多官方店',       '小拼', '400-900-0001', '上海市长宁区通协路269号',   1, NULL),
  (2, 'CUS-DY',  '抖音电商仓',         '小音', '400-900-0002', '天津市武清区电商产业园',     1, NULL),
  (3, 'CUS-SHM', '上海徐汇门店',       '小店', '021-64080003', '上海市徐汇区肇嘉浜路1111号', 1, NULL),
  (4, 'CUS-OLD', '停用客户（示例）',   '无',   NULL,           NULL,                         0, '已流失');

-- =============================================================================
-- 5. 出入库单据主表（先插入不依赖盘点的单据；ADJUST 见第 7 节）
-- =============================================================================

INSERT INTO stock_movement (
  id, movement_no, type, status, warehouse_id, to_warehouse_id,
  partner_type, partner_id, ref_movement_id, ref_stocktake_id,
  total_qty, total_amount, creator_id, approver_id, executor_id,
  submitted_at, approved_at, executed_at, cancelled_at, cancel_reason, reject_reason,
  remark, version, created_at
) VALUES
  -- 采购入库
  (1,  'PI20260901000001', 'PURCHASE_IN', 'COMPLETED',            1, NULL, 'SUPPLIER', 1, NULL, NULL, 520, 11020.00, 4, 1, 2,
      '2026-09-01 02:00:00', '2026-09-01 02:30:00', '2026-09-01 04:00:00', NULL, NULL, NULL,
      '上海仓家居首批到货', 3, '2026-09-01 01:30:00'),
  (2,  'PI20260901000002', 'PURCHASE_IN', 'CANCELLED',            1, NULL, 'SUPPLIER', 1, NULL, NULL,  40,   600.00, 4, NULL, NULL,
      '2026-09-01 03:00:00', NULL, NULL, '2026-09-01 06:00:00', '供应商档期冲突无法交货', NULL,
      NULL, 2, '2026-09-01 02:50:00'),
  (3,  'PI20260902000001', 'PURCHASE_IN', 'COMPLETED',            1, NULL, 'SUPPLIER', 2, NULL, NULL, 740,  5350.00, 4, 1, 2,
      '2026-09-02 01:00:00', '2026-09-02 01:20:00', '2026-09-02 04:00:00', NULL, NULL, NULL,
      '上海仓文具与瑜伽垫到货', 3, '2026-09-02 00:40:00'),
  (4,  'PI20260903000001', 'PURCHASE_IN', 'PARTIALLY_COMPLETED',  2, NULL, 'SUPPLIER', 1, NULL, NULL, 155,  2950.00, 4, 1, 3,
      '2026-09-03 01:00:00', '2026-09-03 01:30:00', '2026-09-03 05:00:00', NULL, NULL, NULL,
      '广州仓家居到货，洗衣液 2L 短收 10 瓶后关闭余量', 4, '2026-09-03 00:50:00'),
  (5,  'PI20260904000001', 'PURCHASE_IN', 'COMPLETED',            2, NULL, 'SUPPLIER', 3, NULL, NULL,  80,  3840.00, 4, 1, 3,
      '2026-09-04 01:00:00', '2026-09-04 01:10:00', '2026-09-04 05:00:00', NULL, NULL, NULL,
      '广州仓蛋白粉到货', 3, '2026-09-04 00:30:00'),
  (6,  'PI20260905000001', 'PURCHASE_IN', 'PENDING_APPROVAL',     1, NULL, 'SUPPLIER', 1, NULL, NULL,  30,   450.00, 4, NULL, NULL,
      '2026-09-05 02:00:00', NULL, NULL, NULL, NULL, NULL,
      '待审采购', 1, '2026-09-05 01:40:00'),
  (7,  'PI20260905000002', 'PURCHASE_IN', 'DRAFT',                1, NULL, 'SUPPLIER', 2, NULL, NULL,  80,    96.00, 4, NULL, NULL,
      NULL, NULL, NULL, NULL, NULL, NULL,
      '草稿，未提交', 0, '2026-09-05 03:00:00'),
  (8,  'PI20260905000003', 'PURCHASE_IN', 'REJECTED',             1, NULL, 'SUPPLIER', 3, NULL, NULL,  15,   870.00, 4, 1, NULL,
      '2026-09-05 04:00:00', NULL, NULL, NULL, NULL, '单价偏高，请重新议价',
      NULL, 2, '2026-09-05 03:50:00'),
  (9,  'PI20260908000001', 'PURCHASE_IN', 'APPROVED',             2, NULL, 'SUPPLIER', 3, NULL, NULL,  40,  1920.00, 4, 1, NULL,
      '2026-09-08 01:00:00', '2026-09-08 01:30:00', NULL, NULL, NULL, NULL,
      '已审核，供应商尚未发货', 2, '2026-09-08 00:40:00'),
  (10, 'PI20260909000001', 'PURCHASE_IN', 'RECEIVING',            1, NULL, 'SUPPLIER', 1, NULL, NULL,  25,   550.00, 4, 1, 2,
      '2026-09-09 01:00:00', '2026-09-09 01:20:00', NULL, NULL, NULL, NULL,
      '验货中，尚未确认实收', 3, '2026-09-09 00:50:00'),

  -- 销售出库
  (11, 'SO20260904000001', 'SALE_OUT', 'RESERVE_FAILED', 1, NULL, 'CUSTOMER', 1, NULL, NULL, 400, 23600.00, 5, NULL, NULL,
      '2026-09-04 06:00:00', NULL, NULL, NULL, NULL, NULL,
      '可用量不足，预留失败', 1, '2026-09-04 05:50:00'),
  (12, 'SO20260904000002', 'SALE_OUT', 'DRAFT',          1, NULL, 'CUSTOMER', 3, NULL, NULL,   4,   119.60, 5, NULL, NULL,
      NULL, NULL, NULL, NULL, NULL, NULL,
      '销售草稿', 0, '2026-09-04 07:00:00'),
  (13, 'SO20260906000001', 'SALE_OUT', 'SHIPPED',        1, NULL, 'CUSTOMER', 1, NULL, NULL,  85,  2067.50, 5, 1, 2,
      '2026-09-06 02:00:00', '2026-09-06 02:30:00', '2026-09-06 11:00:00', NULL, NULL, NULL,
      '拼多多官方店 9/6 出库', 4, '2026-09-06 01:40:00'),
  (14, 'SO20260906000002', 'SALE_OUT', 'SHIPPED',        1, NULL, 'CUSTOMER', 2, NULL, NULL,   4,   516.00, 5, 1, 2,
      '2026-09-06 03:00:00', '2026-09-06 03:20:00', '2026-09-06 13:00:00', NULL, NULL, NULL,
      '抖音仓 8mm 瑜伽垫', 4, '2026-09-06 02:50:00'),
  (15, 'SO20260906000003', 'SALE_OUT', 'SHIPPED',        2, NULL, 'CUSTOMER', 3, NULL, NULL,  15,   825.00, 5, 1, 3,
      '2026-09-06 04:00:00', '2026-09-06 04:20:00', '2026-09-06 16:00:00', NULL, NULL, NULL,
      '门店从广州仓提货，该 SKU 出清', 4, '2026-09-06 03:40:00'),
  (16, 'SO20260907000001', 'SALE_OUT', 'RESERVED',       1, NULL, 'CUSTOMER', 1, NULL, NULL,  23,  1240.50, 5, NULL, NULL,
      '2026-09-07 02:00:00', NULL, NULL, NULL, NULL, NULL,
      '已预留，待审核', 1, '2026-09-07 01:50:00'),
  (17, 'SO20260907000002', 'SALE_OUT', 'APPROVED',       1, NULL, 'CUSTOMER', 2, NULL, NULL,  12,   478.80, 5, 1, NULL,
      '2026-09-07 03:00:00', '2026-09-07 03:30:00', NULL, NULL, NULL, NULL,
      '已审核，仓管尚未拣货', 2, '2026-09-07 02:40:00'),
  (18, 'SO20260908000001', 'SALE_OUT', 'PICKING',        1, NULL, 'CUSTOMER', 3, NULL, NULL,   6,   192.00, 5, 1, 2,
      '2026-09-08 02:00:00', '2026-09-08 02:20:00', NULL, NULL, NULL, NULL,
      '拣货中', 3, '2026-09-08 01:50:00'),
  (19, 'SO20260908000002', 'SALE_OUT', 'CANCELLED',      1, NULL, 'CUSTOMER', 1, NULL, NULL,   8,   472.00, 5, NULL, NULL,
      '2026-09-08 03:00:00', NULL, NULL, '2026-09-08 11:00:00', '客户取消订单，释放预留', NULL,
      '预留后取消', 2, '2026-09-08 02:50:00'),
  (20, 'SO20260909000001', 'SALE_OUT', 'SHIPPED',        1, NULL, 'CUSTOMER', 1, NULL, NULL,   8,   239.20, 5, 1, 2,
      '2026-09-09 02:00:00', '2026-09-09 02:20:00', '2026-09-09 14:00:00', NULL, NULL, NULL,
      '后续发生部分退货 2 只', 4, '2026-09-09 01:40:00');

INSERT INTO stock_movement (
  id, movement_no, type, status, warehouse_id, to_warehouse_id,
  partner_type, partner_id, ref_movement_id, ref_stocktake_id,
  total_qty, total_amount, creator_id, approver_id, executor_id,
  submitted_at, approved_at, executed_at, cancelled_at, cancel_reason, reject_reason,
  remark, version, created_at
) VALUES
  -- 销售退货 / 采购退货（依赖原单）
  (21, 'SR20260910000001', 'SALE_RETURN',     'COMPLETED',  1, NULL, 'CUSTOMER', 1, 20, NULL,  2,   59.80, 5, 1, 2,
      '2026-09-10 02:00:00', '2026-09-10 02:20:00', '2026-09-10 11:00:00', NULL, NULL, NULL,
      '良品退回上海仓', 3, '2026-09-10 01:50:00'),
  (22, 'SR20260910000002', 'SALE_RETURN',     'COMPLETED',  4, NULL, 'CUSTOMER', 2, 14, NULL,  1,  129.00, 5, 1, 2,
      '2026-09-10 03:00:00', '2026-09-10 03:15:00', '2026-09-10 14:00:00', NULL, NULL, NULL,
      '次品入退货待检仓', 3, '2026-09-10 02:50:00'),
  (23, 'PR20260909000001', 'PURCHASE_RETURN', 'COMPLETED',  1, NULL, 'SUPPLIER', 2,  3, NULL, 15,   18.00, 4, 1, 2,
      '2026-09-09 03:00:00', '2026-09-09 03:20:00', '2026-09-09 15:00:00', NULL, NULL, NULL,
      '黑色中性笔断墨退供', 3, '2026-09-09 02:40:00'),

  -- 调拨
  (24, 'TF20260905000001', 'TRANSFER', 'COMPLETED',  1, 2, NULL, NULL, NULL, NULL, 15, 225.00, 2, 1, 2,
      '2026-09-05 02:00:00', '2026-09-05 02:20:00', '2026-09-05 15:00:00', NULL, NULL, NULL,
      '上海 -> 广州 小号收纳袋补货，已完成', 4, '2026-09-05 01:50:00'),
  (25, 'TF20260908000001', 'TRANSFER', 'IN_TRANSIT', 1, 2, NULL, NULL, NULL, NULL, 40,  48.00, 2, 1, 2,
      '2026-09-08 04:00:00', '2026-09-08 04:20:00', '2026-09-08 14:00:00', NULL, NULL, NULL,
      '已调出，广州在途 40', 3, '2026-09-08 03:50:00'),
  (26, 'TF20260909000001', 'TRANSFER', 'APPROVED',   1, 2, NULL, NULL, NULL, NULL,  8, 144.00, 2, 1, NULL,
      '2026-09-09 04:00:00', '2026-09-09 04:15:00', NULL, NULL, NULL, NULL,
      '已审核未调出', 2, '2026-09-09 03:50:00'),
  (27, 'TF20260909000002', 'TRANSFER', 'DRAFT',      1, 2, NULL, NULL, NULL, NULL,  4, 180.00, 2, NULL, NULL,
      NULL, NULL, NULL, NULL, NULL, NULL,
      '调拨草稿', 0, '2026-09-09 05:00:00'),

  -- 其它入库
  (28, 'OI20260907000001', 'OTHER_IN', 'COMPLETED', 1, NULL, NULL, NULL, NULL, NULL, 8, 256.00, 2, 1, 2,
      '2026-09-07 04:00:00', '2026-09-07 04:10:00', '2026-09-07 10:00:00', NULL, NULL, NULL,
      '盘点前发现遗漏装箱，其它入库', 3, '2026-09-07 03:50:00');

-- =============================================================================
-- 6. 单据明细（ADJUST 明细与主表一起在第 7 节插入）
-- =============================================================================

INSERT INTO stock_movement_item (
  id, movement_id, sku_id, location_id, planned_qty, actual_qty, returned_qty,
  unit_price, amount, cond, batch_no, expire_date, remark
) VALUES
  -- M1 采购完成 上海家居
  (1,  1,  1, 1, 180, 180, 0,  15.00,  2700.00, NULL, 'B20260901-JN', NULL, NULL),
  (2,  1,  2, 1, 120, 120, 0,  22.00,  2640.00, NULL, 'B20260901-JN', NULL, NULL),
  (3,  1,  3, 1,  60,  60, 0,  28.00,  1680.00, NULL, 'B20260901-JN', NULL, NULL),
  (4,  1,  4, 1,  80,  80, 0,  18.00,  1440.00, NULL, 'B20260901-JN', NULL, NULL),
  (5,  1,  5, 1,  80,  80, 0,  32.00,  2560.00, NULL, 'B20260901-JN', NULL, NULL),
  -- M2 取消
  (6,  2,  1, 1,  40, NULL, 0, 15.00,   600.00, NULL, NULL, NULL, NULL),
  -- M3 采购完成 上海文具/运动
  (7,  3,  6, 2, 400, 400, 15,  1.20,   480.00, NULL, 'B20260902-WZ', NULL, '后续采购退货 15'),
  (8,  3,  7, 2, 250, 250,  0,  1.20,   300.00, NULL, 'B20260902-WZ', NULL, NULL),
  (9,  3,  8, 3,  50,  50,  0, 45.00,  2250.00, NULL, 'B20260902-YD', NULL, NULL),
  (10, 3,  9, 3,  40,  40,  0, 58.00,  2320.00, NULL, 'B20260902-YD', NULL, NULL),
  -- M4 部分完成 广州家居（洗衣液 2L 计划 25 实收 15）
  (11, 4,  1, 4,  70,  70, 0,  15.00,  1050.00, NULL, 'B20260903-JN', NULL, NULL),
  (12, 4,  2, 4,  40,  40, 0,  22.00,   880.00, NULL, 'B20260903-JN', NULL, NULL),
  (13, 4,  4, 4,  30,  30, 0,  18.00,   540.00, NULL, 'B20260903-JN', NULL, NULL),
  (14, 4,  5, 4,  25,  15, 0,  32.00,   480.00, NULL, 'B20260903-JN', NULL, '短收 10 瓶，余量关闭'),
  -- M5 广州蛋白粉
  (15, 5, 10, 4,  80,  80, 0,  48.00,  3840.00, NULL, 'B20260904-YD', '2027-03-01', NULL),
  -- M6~M10 未完成采购
  (16, 6,  1, 1,  30, NULL, 0, 15.00,   450.00, NULL, NULL, NULL, NULL),
  (17, 7,  6, 2,  80, NULL, 0,  1.20,    96.00, NULL, NULL, NULL, NULL),
  (18, 8,  9, 3,  15, NULL, 0, 58.00,   870.00, NULL, NULL, NULL, NULL),
  (19, 9, 10, 4,  40, NULL, 0, 48.00,  1920.00, NULL, NULL, '2027-09-01', NULL),
  (20,10,  2, 1,  25, NULL, 0, 22.00,   550.00, NULL, NULL, NULL, '验货中'),
  -- 销售
  (21,11,  3, 1, 400, NULL, 0, 59.00, 23600.00, NULL, NULL, NULL, '可用量远不足'),
  (22,12,  1, 1,   4, NULL, 0, 29.90,   119.60, NULL, NULL, NULL, NULL),
  (23,13,  1, 1,  25,  25, 0, 29.90,   747.50, NULL, NULL, NULL, NULL),
  (24,13,  6, 2,  40,  40, 0,  3.50,   140.00, NULL, NULL, NULL, NULL),
  (25,13,  3, 1,  20,  20, 0, 59.00,  1180.00, NULL, NULL, NULL, NULL),
  (26,14,  9, 3,   4,   4, 1,129.00,   516.00, NULL, NULL, NULL, '其中 1 张已退货（次品）'),
  (27,15,  5, 4,  15,  15, 0, 55.00,   825.00, NULL, NULL, NULL, NULL),
  (28,16,  1, 1,  15, NULL, 0, 29.90,   448.50, NULL, NULL, NULL, '已预留未出库'),
  (29,16,  8, 3,   8, NULL, 0, 99.00,   792.00, NULL, NULL, NULL, '已预留未出库'),
  (30,17,  2, 1,  12, NULL, 0, 39.90,   478.80, NULL, NULL, NULL, '已预留已审核'),
  (31,18,  4, 1,   6, NULL, 0, 32.00,   192.00, NULL, NULL, NULL, '拣货中'),
  (32,19,  3, 1,   8, NULL, 0, 59.00,   472.00, NULL, NULL, NULL, '已释放预留'),
  (33,20,  1, 1,   8,   8, 2, 29.90,   239.20, NULL, NULL, NULL, '已退 2 只'),
  -- 退货
  (34,21,  1, 1,   2,   2, 0, 29.90,    59.80, 'GOOD',      NULL, NULL, '良品回原库位'),
  (35,22,  9, 0,   1,   1, 0,129.00,   129.00, 'DEFECTIVE', NULL, NULL, '次品入待检仓'),
  (36,23,  6, 2,  15,  15, 0,  1.20,    18.00, NULL,        NULL, NULL, NULL),
  -- 调拨 / 其它入
  (37,24,  1, 1,  15,  15, 0, 15.00,   225.00, NULL, NULL, NULL, '源库位 A-01-01，目标广州默认库位'),
  (38,25,  7, 2,  40,  40, 0,  1.20,    48.00, NULL, NULL, NULL, '已调出，目标仓在途'),
  (39,26,  4, 1,   8, NULL, 0, 18.00,   144.00, NULL, NULL, NULL, NULL),
  (40,27,  8, 3,   4, NULL, 0, 45.00,   180.00, NULL, NULL, NULL, NULL),
  (41,28,  5, 1,   8,   8, 0, 32.00,   256.00, NULL, NULL, NULL, NULL);

-- =============================================================================
-- 7. 盘点 + 盘盈盘亏调整单（循环 FK：先盘点，再 ADJUST，再回写 adjust_movement_id）
-- =============================================================================

INSERT INTO stocktake (
  id, stocktake_no, warehouse_id, scope, scope_value, status,
  snapshot_at, creator_id, approver_id, adjust_movement_id,
  gain_qty, loss_qty, gain_amount, loss_amount,
  submitted_at, approved_at, cancelled_at, reject_reason, remark, version, created_at
) VALUES
  (1, 'ST20260905000001', 1, 'BY_SKU',      '[3]',    'COMPLETED',
      '2026-09-05 09:00:00', 2, 1, NULL,
      0, 3, 0.00, 84.00,
      '2026-09-05 14:00:00', '2026-09-05 16:00:00', NULL, NULL,
      '上海仓抽盘特大号收纳袋', 3, '2026-09-05 08:50:00'),
  (2, 'ST20260908000001', 1, 'BY_CATEGORY', '[2]',    'CANCELLED',
      NULL, 2, NULL, NULL,
      0, 0, 0.00, 0.00,
      NULL, NULL, '2026-09-08 12:00:00', NULL,
      '创建后发现范围重叠，取消', 1, '2026-09-08 08:00:00'),
  (3, 'ST20260909000001', 2, 'ALL',         NULL,     'CREATED',
      NULL, 3, NULL, NULL,
      0, 0, 0.00, 0.00,
      NULL, NULL, NULL, NULL,
      '广州全仓盘点任务，尚未锁定', 0, '2026-09-09 08:00:00'),
  (4, 'ST20260910000001', 2, 'BY_LOCATION', '[4]',    'COUNTING',
      '2026-09-10 01:00:00', 3, NULL, NULL,
      0, 0, 0.00, 0.00,
      NULL, NULL, NULL, NULL,
      '广州默认库位盘点中，相关库存已锁定', 1, '2026-09-10 00:40:00');

INSERT INTO stock_movement (
  id, movement_no, type, status, warehouse_id, to_warehouse_id,
  partner_type, partner_id, ref_movement_id, ref_stocktake_id,
  total_qty, total_amount, creator_id, approver_id, executor_id,
  submitted_at, approved_at, executed_at, cancelled_at, cancel_reason, reject_reason,
  remark, version, created_at
) VALUES
  (29, 'AJ20260905000001', 'ADJUST', 'COMPLETED', 1, NULL, NULL, NULL, NULL, 1,
      3, 84.00, 1, 1, 1,
      '2026-09-05 16:00:00', '2026-09-05 16:00:00', '2026-09-05 16:00:00', NULL, NULL, NULL,
      '盘点 ST20260905000001 盘亏生效（管理员自审，需审计）', 1, '2026-09-05 16:00:00');

INSERT INTO stock_movement_item (
  id, movement_id, sku_id, location_id, planned_qty, actual_qty, returned_qty,
  unit_price, amount, cond, batch_no, expire_date, remark
) VALUES
  (42, 29, 3, 1, 3, 3, 0, 28.00, 84.00, NULL, NULL, NULL, '盘亏 3 只');

UPDATE stocktake SET adjust_movement_id = 29 WHERE id = 1;

INSERT INTO stocktake_item (
  id, stocktake_id, sku_id, location_id, snapshot_qty, counted_qty, diff_qty,
  diff_reason, excluded, counted_by, counted_at
) VALUES
  (1, 1, 3, 1,  60,  57,  -3, '抽检破损报废',         0, 2, '2026-09-05 13:00:00'),
  (2, 4, 1, 4,  85,  83,  -2, '包装破损少计',         0, 3, '2026-09-10 03:00:00'),
  (3, 4, 2, 4,  40, NULL, NULL, NULL,                0, NULL, NULL),
  (4, 4, 4, 4,  30,  30,   0, NULL,                  0, 3, '2026-09-10 03:10:00'),
  (5, 4, 5, 4,   0,   0,   0, '已售罄',              0, 3, '2026-09-10 03:15:00'),
  (6, 4,10, 4,  80,  82,   2, '收货未及时上账待核',   0, 3, '2026-09-10 03:20:00');

-- =============================================================================
-- 8. 库存主表（当前时点快照，必须满足 available = on_hand - reserved）
--
-- inv1  BAG-S    SH A-01-01 : 入180 -调出15 -售25 -售8 +退2 = 134，预留 15
-- inv2  BAG-L    SH A-01-01 : 入120，预留 12
-- inv3  BAG-XL   SH A-01-01 : 入60 -盘亏3 -售20 = 37（取消单已释放）
-- inv4  DET-1L   SH A-01-01 : 入80，预留 6（拣货中）
-- inv5  DET-2L   SH A-01-01 : 入80 +其它入8 = 88
-- inv6  PEN-BLK  SH A-01-02 : 入400 -售40 -退供15 = 345  （超储）
-- inv7  PEN-BLU  SH A-01-02 : 入250 -调出40 = 210
-- inv8  YOGA-6   SH B-02-01 : 入50，预留 8
-- inv9  YOGA-8   SH B-02-01 : 入40 -售4 = 36
-- inv10 BAG-S    GZ A-01-01 : 入70 +调入15 = 85（盘点锁定）
-- inv11 BAG-L    GZ A-01-01 : 入40（盘点锁定）
-- inv12 DET-1L   GZ A-01-01 : 入30（盘点锁定）
-- inv13 DET-2L   GZ A-01-01 : 入15 -售15 = 0（盘点锁定 / 零库存预警）
-- inv14 PROT-CHO GZ A-01-01 : 入80（盘点锁定）
-- inv15 PEN-BLU  GZ 未指定  : 在途 40
-- inv16 YOGA-8   QC 未指定  : 次品退货 1
-- =============================================================================

INSERT INTO inventory (
  id, sku_id, warehouse_id, location_id,
  on_hand_qty, reserved_qty, available_qty, in_transit_qty,
  locked, lock_stocktake_id, version, created_at, updated_at
) VALUES
  (1,  1, 1, 1, 134, 15, 119,  0, 0, NULL, 8, '2026-09-01 04:00:00', '2026-09-10 11:00:00'),
  (2,  2, 1, 1, 120, 12, 108,  0, 0, NULL, 2, '2026-09-01 04:00:00', '2026-09-07 03:00:00'),
  (3,  3, 1, 1,  37,  0,  37,  0, 0, NULL, 6, '2026-09-01 04:00:00', '2026-09-08 11:00:00'),
  (4,  4, 1, 1,  80,  6,  74,  0, 0, NULL, 2, '2026-09-01 04:00:00', '2026-09-08 02:00:00'),
  (5,  5, 1, 1,  88,  0,  88,  0, 0, NULL, 2, '2026-09-01 04:00:00', '2026-09-07 10:00:00'),
  (6,  6, 1, 2, 345,  0, 345,  0, 0, NULL, 4, '2026-09-02 04:00:00', '2026-09-09 15:00:00'),
  (7,  7, 1, 2, 210,  0, 210,  0, 0, NULL, 2, '2026-09-02 04:00:00', '2026-09-08 14:00:00'),
  (8,  8, 1, 3,  50,  8,  42,  0, 0, NULL, 2, '2026-09-02 04:00:00', '2026-09-07 02:00:00'),
  (9,  9, 1, 3,  36,  0,  36,  0, 0, NULL, 3, '2026-09-02 04:00:00', '2026-09-06 13:00:00'),
  (10, 1, 2, 4,  85,  0,  85,  0, 1, 4,    3, '2026-09-03 05:00:00', '2026-09-10 01:00:00'),
  (11, 2, 2, 4,  40,  0,  40,  0, 1, 4,    2, '2026-09-03 05:00:00', '2026-09-10 01:00:00'),
  (12, 4, 2, 4,  30,  0,  30,  0, 1, 4,    2, '2026-09-03 05:00:00', '2026-09-10 01:00:00'),
  (13, 5, 2, 4,   0,  0,   0,  0, 1, 4,    4, '2026-09-03 05:00:00', '2026-09-10 01:00:00'),
  (14,10, 2, 4,  80,  0,  80,  0, 1, 4,    2, '2026-09-04 05:00:00', '2026-09-10 01:00:00'),
  (15, 7, 2, 0,   0,  0,   0, 40, 0, NULL, 0, '2026-09-08 14:00:00', '2026-09-08 14:00:00'),
  (16, 9, 4, 0,   1,  0,   1,  0, 0, NULL, 1, '2026-09-10 14:00:00', '2026-09-10 14:00:00');

-- =============================================================================
-- 9. 库存变动日志（只增；before/after 与当前库存终态对齐）
-- delta_qty：IN/RETURN_IN/TRANSFER_IN/ADJUST_GAIN/RESERVE 为正；
--            OUT/RETURN_OUT/TRANSFER_OUT/ADJUST_LOSS/RELEASE 为负
-- =============================================================================

INSERT INTO inventory_log (
  id, inventory_id, sku_id, warehouse_id, location_id, change_type, delta_qty,
  on_hand_before, on_hand_after, reserved_before, reserved_after,
  available_before, available_after,
  movement_id, movement_item_id, stocktake_id, operator_id, trace_id, remark, operated_at
) VALUES
  -- 09-01 上海家居采购入库
  (1,  1, 1, 1, 1, 'IN', 180,   0, 180, 0, 0,   0, 180, 1, 1, NULL, 2, 'tr-pi-20260901-000001', '采购入库', '2026-09-01 04:00:00.000'),
  (2,  2, 2, 1, 1, 'IN', 120,   0, 120, 0, 0,   0, 120, 1, 2, NULL, 2, 'tr-pi-20260901-000001', '采购入库', '2026-09-01 04:00:00.010'),
  (3,  3, 3, 1, 1, 'IN',  60,   0,  60, 0, 0,   0,  60, 1, 3, NULL, 2, 'tr-pi-20260901-000001', '采购入库', '2026-09-01 04:00:00.020'),
  (4,  4, 4, 1, 1, 'IN',  80,   0,  80, 0, 0,   0,  80, 1, 4, NULL, 2, 'tr-pi-20260901-000001', '采购入库', '2026-09-01 04:00:00.030'),
  (5,  5, 5, 1, 1, 'IN',  80,   0,  80, 0, 0,   0,  80, 1, 5, NULL, 2, 'tr-pi-20260901-000001', '采购入库', '2026-09-01 04:00:00.040'),

  -- 09-02 上海文具/运动采购入库
  (6,  6, 6, 1, 2, 'IN', 400,   0, 400, 0, 0,   0, 400, 3, 7,  NULL, 2, 'tr-pi-20260902-000001', '采购入库', '2026-09-02 04:00:00.000'),
  (7,  7, 7, 1, 2, 'IN', 250,   0, 250, 0, 0,   0, 250, 3, 8,  NULL, 2, 'tr-pi-20260902-000001', '采购入库', '2026-09-02 04:00:00.010'),
  (8,  8, 8, 1, 3, 'IN',  50,   0,  50, 0, 0,   0,  50, 3, 9,  NULL, 2, 'tr-pi-20260902-000001', '采购入库', '2026-09-02 04:00:00.020'),
  (9,  9, 9, 1, 3, 'IN',  40,   0,  40, 0, 0,   0,  40, 3, 10, NULL, 2, 'tr-pi-20260902-000001', '采购入库', '2026-09-02 04:00:00.030'),

  -- 09-03 广州家居采购入库
  (10, 10, 1, 2, 4, 'IN',  70,   0,  70, 0, 0,   0,  70, 4, 11, NULL, 3, 'tr-pi-20260903-000001', '采购入库', '2026-09-03 05:00:00.000'),
  (11, 11, 2, 2, 4, 'IN',  40,   0,  40, 0, 0,   0,  40, 4, 12, NULL, 3, 'tr-pi-20260903-000001', '采购入库', '2026-09-03 05:00:00.010'),
  (12, 12, 4, 2, 4, 'IN',  30,   0,  30, 0, 0,   0,  30, 4, 13, NULL, 3, 'tr-pi-20260903-000001', '采购入库', '2026-09-03 05:00:00.020'),
  (13, 13, 5, 2, 4, 'IN',  15,   0,  15, 0, 0,   0,  15, 4, 14, NULL, 3, 'tr-pi-20260903-000001', '短收后实收', '2026-09-03 05:00:00.030'),

  -- 09-04 广州蛋白粉入库
  (14, 14,10, 2, 4, 'IN',  80,   0,  80, 0, 0,   0,  80, 5, 15, NULL, 3, 'tr-pi-20260904-000001', '采购入库', '2026-09-04 05:00:00.000'),

  -- 09-05 调拨完成：上海出、广州入
  (15,  1, 1, 1, 1, 'TRANSFER_OUT', -15, 180, 165, 0, 0, 180, 165, 24, 37, NULL, 2, 'tr-tf-20260905-000001', '调出上海', '2026-09-05 10:00:00.000'),
  (16, 10, 1, 2, 4, 'TRANSFER_IN',   15,  70,  85, 0, 0,  70,  85, 24, 37, NULL, 3, 'tr-tf-20260905-000001', '调入广州', '2026-09-05 15:00:00.000'),

  -- 09-05 盘亏
  (17,  3, 3, 1, 1, 'ADJUST_LOSS',  -3,  60,  57, 0, 0,  60,  57, 29, 42, 1, 1, 'tr-aj-20260905-000001', '盘点盘亏', '2026-09-05 16:00:00.000'),

  -- 09-06 销售 SO20260906000001：预留后出库（出库阶段 available 不变）
  (18,  1, 1, 1, 1, 'RESERVE',  25, 165, 165,  0, 25, 165, 140, 13, 23, NULL, 5, 'tr-so-20260906-000001', '销售预留', '2026-09-06 08:00:00.000'),
  (19,  6, 6, 1, 2, 'RESERVE',  40, 400, 400,  0, 40, 400, 360, 13, 24, NULL, 5, 'tr-so-20260906-000001', '销售预留', '2026-09-06 08:00:00.010'),
  (20,  3, 3, 1, 1, 'RESERVE',  20,  57,  57,  0, 20,  57,  37, 13, 25, NULL, 5, 'tr-so-20260906-000001', '销售预留', '2026-09-06 08:00:00.020'),
  (21,  1, 1, 1, 1, 'OUT',     -25, 165, 140, 25,  0, 140, 140, 13, 23, NULL, 2, 'tr-so-20260906-000001', '确认出库', '2026-09-06 11:00:00.000'),
  (22,  6, 6, 1, 2, 'OUT',     -40, 400, 360, 40,  0, 360, 360, 13, 24, NULL, 2, 'tr-so-20260906-000001', '确认出库', '2026-09-06 11:00:00.010'),
  (23,  3, 3, 1, 1, 'OUT',     -20,  57,  37, 20,  0,  37,  37, 13, 25, NULL, 2, 'tr-so-20260906-000001', '确认出库', '2026-09-06 11:00:00.020'),

  -- 09-06 8mm 瑜伽垫出库
  (24,  9, 9, 1, 3, 'RESERVE',   4,  40,  40,  0,  4,  40,  36, 14, 26, NULL, 5, 'tr-so-20260906-000002', '销售预留', '2026-09-06 09:00:00.000'),
  (25,  9, 9, 1, 3, 'OUT',      -4,  40,  36,  4,  0,  36,  36, 14, 26, NULL, 2, 'tr-so-20260906-000002', '确认出库', '2026-09-06 13:00:00.000'),

  -- 09-06 广州洗衣液 2L 出清
  (26, 13, 5, 2, 4, 'RESERVE',  15,  15,  15,  0, 15,  15,   0, 15, 27, NULL, 5, 'tr-so-20260906-000003', '销售预留', '2026-09-06 14:00:00.000'),
  (27, 13, 5, 2, 4, 'OUT',     -15,  15,   0, 15,  0,   0,   0, 15, 27, NULL, 3, 'tr-so-20260906-000003', '确认出库', '2026-09-06 16:00:00.000'),

  -- 09-07 预留未出库 + 其它入库
  (28,  1, 1, 1, 1, 'RESERVE',  15, 140, 140,  0, 15, 140, 125, 16, 28, NULL, 5, 'tr-so-20260907-000001', '销售预留', '2026-09-07 02:00:00.000'),
  (29,  8, 8, 1, 3, 'RESERVE',   8,  50,  50,  0,  8,  50,  42, 16, 29, NULL, 5, 'tr-so-20260907-000001', '销售预留', '2026-09-07 02:00:00.010'),
  (30,  2, 2, 1, 1, 'RESERVE',  12, 120, 120,  0, 12, 120, 108, 17, 30, NULL, 5, 'tr-so-20260907-000002', '销售预留', '2026-09-07 03:00:00.000'),
  (31,  5, 5, 1, 1, 'IN',        8,  80,  88,  0,  0,  80,  88, 28, 41, NULL, 2, 'tr-oi-20260907-000001', '其它入库', '2026-09-07 10:00:00.000'),

  -- 09-08 拣货中预留 / 取消释放 / 调拨在途
  (32,  4, 4, 1, 1, 'RESERVE',   6,  80,  80,  0,  6,  80,  74, 18, 31, NULL, 5, 'tr-so-20260908-000001', '销售预留', '2026-09-08 02:00:00.000'),
  (33,  3, 3, 1, 1, 'RESERVE',   8,  37,  37,  0,  8,  37,  29, 19, 32, NULL, 5, 'tr-so-20260908-000002', '销售预留', '2026-09-08 03:00:00.000'),
  (34,  3, 3, 1, 1, 'RELEASE',  -8,  37,  37,  8,  0,  29,  37, 19, 32, NULL, 5, 'tr-so-20260908-000002', '取消释放预留', '2026-09-08 11:00:00.000'),
  (35,  7, 7, 1, 2, 'TRANSFER_OUT', -40, 250, 210, 0, 0, 250, 210, 25, 38, NULL, 2, 'tr-tf-20260908-000001', '调出，目标仓记在途', '2026-09-08 14:00:00.000'),

  -- 09-09 再出一笔小号袋 + 采购退货
  (36,  1, 1, 1, 1, 'RESERVE',   8, 140, 140, 15, 23, 125, 117, 20, 33, NULL, 5, 'tr-so-20260909-000001', '销售预留', '2026-09-09 02:00:00.000'),
  (37,  1, 1, 1, 1, 'OUT',      -8, 140, 132, 23, 15, 117, 117, 20, 33, NULL, 2, 'tr-so-20260909-000001', '确认出库', '2026-09-09 14:00:00.000'),
  (38,  6, 6, 1, 2, 'RETURN_OUT', -15, 360, 345, 0, 0, 360, 345, 23, 36, NULL, 2, 'tr-pr-20260909-000001', '采购退货出库', '2026-09-09 15:00:00.000'),

  -- 09-10 销售退货
  (39,  1, 1, 1, 1, 'RETURN_IN',  2, 132, 134, 15, 15, 117, 119, 21, 34, NULL, 2, 'tr-sr-20260910-000001', '良品退货入库', '2026-09-10 11:00:00.000'),
  (40, 16, 9, 4, 0, 'RETURN_IN',  1,   0,   1,  0,  0,   0,   1, 22, 35, NULL, 2, 'tr-sr-20260910-000002', '次品入待检仓', '2026-09-10 14:00:00.000');

-- =============================================================================
-- 10. 安全库存规则 / 预警
-- warehouse_id = 0 表示全局规则
-- =============================================================================

INSERT INTO safety_stock_rule (id, sku_id, warehouse_id, min_qty, max_qty, enabled) VALUES
  (1, 3, 0,  20, 150, 1),   -- 特大号袋全局
  (2, 3, 1,  40, 120, 1),   -- 特大号袋上海覆盖全局 → 当前 37 触发 LOW
  (3, 6, 1,  50, 200, 1),   -- 黑笔上海 max=200，当前 345 触发 OVER
  (4, 5, 2,   8,  60, 1),   -- 洗衣液 2L 广州 min=8，当前 0 触发 ZERO
  (5,10, 2,  40, 200, 1),   -- 蛋白粉广州，当前 80 正常
  (6, 1, 1,  30, 300, 1),   -- 小号袋上海，当前 119 正常
  (7, 8, 0,  10,  80, 1),   -- 6mm 垫全局
  (8, 7, 1,  30, 400, 0);   -- 已停用规则

INSERT INTO stock_alert (
  id, rule_id, sku_id, warehouse_id, alert_type, current_qty, threshold,
  status, handled_by, handled_at, created_at
) VALUES
  (1, 2, 3, 1, 'LOW',   37,  40, 'OPEN',   NULL, NULL,                  '2026-09-06 11:01:00'),
  (2, 3, 6, 1, 'OVER', 345, 200, 'OPEN',   NULL, NULL,                  '2026-09-09 15:01:00'),
  (3, 4, 5, 2, 'ZERO',   0,   8, 'OPEN',   NULL, NULL,                  '2026-09-06 16:01:00'),
  (4, 7, 8, 1, 'LOW',    6,  10, 'CLOSED', 2,    '2026-09-03 08:00:00', '2026-09-02 10:00:00'),
  (5, 5,10, 2, 'LOW',   35,  40, 'ACKED',  3,    '2026-09-04 06:00:00', '2026-09-04 05:30:00');

-- =============================================================================
-- 11. 审计 / 单号序列 / 幂等键
-- =============================================================================

INSERT INTO sys_audit_log (id, user_id, action, resource_type, resource_id, detail, ip, user_agent, trace_id, created_at) VALUES
  (1, 1, 'LOGIN',             'USER',           '1',  '{"username":"admin"}',                         '127.0.0.1', 'Mozilla/5.0', 'tr-login-admin-001',          '2026-09-11 01:00:00.000'),
  (2, 5, 'LOGIN',             'USER',           '5',  '{"username":"biz_sale"}',                      '127.0.0.1', 'Mozilla/5.0', 'tr-login-sale-001',           '2026-09-11 00:10:00.000'),
  (3, 1, 'APPROVE_MOVEMENT',  'STOCK_MOVEMENT', '1',  '{"movementNo":"PI20260901000001"}',            '127.0.0.1', 'Mozilla/5.0', 'tr-pi-20260901-000001',       '2026-09-01 02:30:00.000'),
  (4, 1, 'APPROVE_MOVEMENT',  'STOCK_MOVEMENT', '13', '{"movementNo":"SO20260906000001"}',            '127.0.0.1', 'Mozilla/5.0', 'tr-so-20260906-000001',       '2026-09-06 02:30:00.000'),
  (5, 1, 'APPROVE_MOVEMENT',  'STOCKTAKE',      '1',  '{"stocktakeNo":"ST20260905000001","selfApprove":true}', '127.0.0.1', 'Mozilla/5.0', 'tr-aj-20260905-000001', '2026-09-05 16:00:00.000'),
  (6, 2, 'CANCEL_MOVEMENT',   'STOCK_MOVEMENT', '19', '{"movementNo":"SO20260908000002"}',            '10.0.0.12', 'Mozilla/5.0', 'tr-so-20260908-000002',       '2026-09-08 11:00:00.000'),
  (7, 3, 'LOCK_INVENTORY',    'STOCKTAKE',      '4',  '{"stocktakeNo":"ST20260910000001","locationId":4}', '10.0.0.22', 'Mozilla/5.0', 'tr-st-20260910-000001', '2026-09-10 01:00:00.000'),
  (8, 1, 'REJECT_MOVEMENT',   'STOCK_MOVEMENT', '8',  '{"movementNo":"PI20260905000003","reason":"单价偏高，请重新议价"}', '127.0.0.1', 'Mozilla/5.0', 'tr-pi-20260905-000003', '2026-09-05 04:30:00.000');

INSERT INTO sys_doc_sequence (prefix, biz_date, current_seq) VALUES
  ('PI', '2026-09-01', 2),
  ('PI', '2026-09-02', 1),
  ('PI', '2026-09-03', 1),
  ('PI', '2026-09-04', 1),
  ('PI', '2026-09-05', 3),
  ('PI', '2026-09-08', 1),
  ('PI', '2026-09-09', 1),
  ('SO', '2026-09-04', 2),
  ('SO', '2026-09-06', 3),
  ('SO', '2026-09-07', 2),
  ('SO', '2026-09-08', 2),
  ('SO', '2026-09-09', 1),
  ('SR', '2026-09-10', 2),
  ('PR', '2026-09-09', 1),
  ('TF', '2026-09-05', 1),
  ('TF', '2026-09-08', 1),
  ('TF', '2026-09-09', 2),
  ('OI', '2026-09-07', 1),
  ('AJ', '2026-09-05', 1),
  ('ST', '2026-09-05', 1),
  ('ST', '2026-09-08', 1),
  ('ST', '2026-09-09', 1),
  ('ST', '2026-09-10', 1);

INSERT INTO sys_idempotency_key (idem_key, request_hash, response_body, created_at, expire_at) VALUES
  ('seed-create-so-20260906-000001', 'sha256:demo-hash-so-000001', '{"code":0,"message":"ok"}', '2026-09-06 01:40:00', '2026-09-07 01:40:00'),
  ('seed-create-pi-20260909-000001', 'sha256:demo-hash-pi-000001', '{"code":0,"message":"ok"}', '2026-09-09 00:50:00', '2026-09-10 00:50:00');

-- 显式 ID 后对齐自增，避免后续应用插入冲突
ALTER TABLE sys_user AUTO_INCREMENT = 7;
ALTER TABLE product_category AUTO_INCREMENT = 7;
ALTER TABLE product AUTO_INCREMENT = 7;
ALTER TABLE product_sku AUTO_INCREMENT = 12;
ALTER TABLE warehouse AUTO_INCREMENT = 5;
ALTER TABLE location AUTO_INCREMENT = 6;
ALTER TABLE supplier AUTO_INCREMENT = 5;
ALTER TABLE customer AUTO_INCREMENT = 5;
ALTER TABLE stock_movement AUTO_INCREMENT = 30;
ALTER TABLE stock_movement_item AUTO_INCREMENT = 43;
ALTER TABLE stocktake AUTO_INCREMENT = 5;
ALTER TABLE stocktake_item AUTO_INCREMENT = 7;
ALTER TABLE inventory AUTO_INCREMENT = 17;
ALTER TABLE inventory_log AUTO_INCREMENT = 41;
ALTER TABLE safety_stock_rule AUTO_INCREMENT = 9;
ALTER TABLE stock_alert AUTO_INCREMENT = 6;
ALTER TABLE sys_audit_log AUTO_INCREMENT = 9;

-- =============================================================================
-- 12. 导入后自检（期望均为 0 行 / 与注释一致）
-- =============================================================================

-- 库存不变量：应返回 0 行
SELECT id, sku_id, warehouse_id, location_id, on_hand_qty, reserved_qty, available_qty
  FROM inventory
 WHERE available_qty <> on_hand_qty - reserved_qty
    OR on_hand_qty < 0 OR reserved_qty < 0 OR available_qty < 0 OR in_transit_qty < 0;

-- 日志终态应与库存快照一致（按 inventory_id 取最后一条）
SELECT i.id,
       i.on_hand_qty   AS inv_on_hand,
       l.on_hand_after AS log_on_hand,
       i.reserved_qty  AS inv_reserved,
       l.reserved_after AS log_reserved,
       i.available_qty AS inv_available,
       l.available_after AS log_available
  FROM inventory i
  JOIN inventory_log l ON l.id = (
        SELECT MAX(x.id) FROM inventory_log x WHERE x.inventory_id = i.id
      )
 WHERE i.on_hand_qty <> l.on_hand_after
    OR i.reserved_qty <> l.reserved_after
    OR i.available_qty <> l.available_after;

SELECT 'seed-test-data loaded' AS status,
       (SELECT COUNT(*) FROM sys_user) AS users,
       (SELECT COUNT(*) FROM product_sku) AS skus,
       (SELECT COUNT(*) FROM inventory) AS inventory_rows,
       (SELECT COUNT(*) FROM stock_movement) AS movements,
       (SELECT COUNT(*) FROM inventory_log) AS logs,
       (SELECT COUNT(*) FROM stock_alert) AS alerts;
