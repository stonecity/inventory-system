-- =============================================================================
-- IMS 时区数据修正：将所有 DATETIME 列整体平移 +8 小时
--
-- 背景：
--   上一轮迁移把 TIMESTAMP 改为 DATETIME 时，MySQL 不会自动把内部 UTC 值
--   转成北京壁钟时，所以列里现在存的是"以 UTC 数值呈现的壁钟字符串"。
--   应用侧按 +08:00 解读后反而比真实时刻慢 8 小时。
--
-- 修正：
--   对每一列执行 col = col + INTERVAL 8 HOUR，把存储的 UTC 数值变成北京壁钟时。
--   对于带 ON UPDATE CURRENT_TIMESTAMP 的列（如 updated_at），在 SET 中显式赋值
--   即可避免触发 ON UPDATE 覆盖为 NOW()，从而保留 +8h 的平移值。
-- =============================================================================

USE inventory_system;

SET time_zone = '+08:00';

-- 1. 认证与权限
UPDATE sys_user
   SET last_login_at = last_login_at + INTERVAL 8 HOUR,
       created_at    = created_at    + INTERVAL 8 HOUR,
       updated_at    = updated_at    + INTERVAL 8 HOUR;

UPDATE sys_role
   SET created_at = created_at + INTERVAL 8 HOUR,
       updated_at = updated_at + INTERVAL 8 HOUR;

UPDATE sys_user_role
   SET created_at = created_at + INTERVAL 8 HOUR;

-- 2. 基础数据
UPDATE product_category
   SET created_at = created_at + INTERVAL 8 HOUR,
       updated_at = updated_at + INTERVAL 8 HOUR;

UPDATE product
   SET created_at = created_at + INTERVAL 8 HOUR,
       updated_at = updated_at + INTERVAL 8 HOUR;

UPDATE product_sku
   SET created_at = created_at + INTERVAL 8 HOUR,
       updated_at = updated_at + INTERVAL 8 HOUR;

UPDATE warehouse
   SET created_at = created_at + INTERVAL 8 HOUR,
       updated_at = updated_at + INTERVAL 8 HOUR;

UPDATE location
   SET created_at = created_at + INTERVAL 8 HOUR,
       updated_at = updated_at + INTERVAL 8 HOUR;

UPDATE sys_user_warehouse
   SET created_at = created_at + INTERVAL 8 HOUR;

UPDATE supplier
   SET created_at = created_at + INTERVAL 8 HOUR,
       updated_at = updated_at + INTERVAL 8 HOUR;

UPDATE customer
   SET created_at = created_at + INTERVAL 8 HOUR,
       updated_at = updated_at + INTERVAL 8 HOUR;

-- 3. 出入库单据
UPDATE stock_movement
   SET submitted_at = submitted_at + INTERVAL 8 HOUR,
       approved_at  = approved_at  + INTERVAL 8 HOUR,
       executed_at  = executed_at  + INTERVAL 8 HOUR,
       cancelled_at = cancelled_at + INTERVAL 8 HOUR,
       created_at   = created_at   + INTERVAL 8 HOUR,
       updated_at   = updated_at   + INTERVAL 8 HOUR;

UPDATE stock_movement_item
   SET created_at = created_at + INTERVAL 8 HOUR,
       updated_at = updated_at + INTERVAL 8 HOUR;

-- 4. 盘点
UPDATE stocktake
   SET snapshot_at  = snapshot_at  + INTERVAL 8 HOUR,
       submitted_at = submitted_at + INTERVAL 8 HOUR,
       approved_at  = approved_at  + INTERVAL 8 HOUR,
       cancelled_at = cancelled_at + INTERVAL 8 HOUR,
       created_at   = created_at   + INTERVAL 8 HOUR,
       updated_at   = updated_at   + INTERVAL 8 HOUR;

UPDATE stocktake_item
   SET counted_at = counted_at + INTERVAL 8 HOUR,
       created_at = created_at + INTERVAL 8 HOUR,
       updated_at = updated_at + INTERVAL 8 HOUR;

-- 5. 库存主表 & 变动日志
UPDATE inventory
   SET created_at = created_at + INTERVAL 8 HOUR,
       updated_at = updated_at + INTERVAL 8 HOUR;

UPDATE inventory_log
   SET operated_at = operated_at + INTERVAL 8 HOUR;

-- 6. 安全库存与预警
UPDATE safety_stock_rule
   SET created_at = created_at + INTERVAL 8 HOUR,
       updated_at = updated_at + INTERVAL 8 HOUR;

UPDATE stock_alert
   SET handled_at = handled_at + INTERVAL 8 HOUR,
       created_at = created_at + INTERVAL 8 HOUR,
       updated_at = updated_at + INTERVAL 8 HOUR;

-- 7. 系统
UPDATE sys_audit_log
   SET created_at = created_at + INTERVAL 8 HOUR;

UPDATE sys_setting
   SET updated_at = updated_at + INTERVAL 8 HOUR;

UPDATE sys_idempotency_key
   SET created_at = created_at + INTERVAL 8 HOUR,
       expire_at  = expire_at  + INTERVAL 8 HOUR;