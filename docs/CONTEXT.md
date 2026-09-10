# IMS 全局 Context

> 本文件是仓库的 **AI / 开发全局上下文**。实现功能、改架构、写接口前先读本文；细节状态机、字段级定义、API 清单以 [PRD.md](./PRD.md) 为准。不要把 PRD 全文复制进代码注释。

| 项 | 值 |
|---|---|
| 项目 | 库存管理系统 (IMS) |
| Group / Artifact | `com.dream` / `inventory` |
| 根包 | `com.dream.inventory` |
| 文档版本对齐 | PRD v1.0 |
| 适用范围 | 中小企业多仓库商品库存 |

---

## 1. 技术栈

| 层 | 选型 | 说明 |
|---|---|---|
| 语言 | Java 17 | 见 `pom.xml` |
| 框架 | Spring Boot 4.1.1 | Web MVC + Data JPA |
| 持久化 | Spring Data JPA + MySQL 8.0.16+ | InnoDB、utf8mb4、`READ COMMITTED` |
| 实体 | Lombok | `@Getter/@Setter/@Builder` + JPA 关系映射 |
| 鉴权（规划） | JWT + `@PreAuthorize` | 密码 BCrypt |
| 前端 | 单文件 HTML + Vue 3 (CDN) + Element Plus (CDN) | 仅放 `src/main/resources/static/` |
| 迁移 | 开发可用 `ddl-auto: update`；生产必须 `validate` + Flyway/Liquibase | CHECK 约束 Hibernate 无法可靠自动生成 |

本地库：`inventory_system`，时区 UTC。配置见 `src/main/resources/application.yml`。

---

## 2. 文档地图

| 文档 | 用途 | 何时读 |
|---|---|---|
| `docs/CONTEXT.md`（本文） | 架构铁律、包结构、不变量、当前基线 | **每次改代码默认已掌握** |
| `docs/PRD.md` | 完整需求、状态机、ER、API、错误码 | 实现某业务域或对字段/流转有疑问时 |
| `.cursorrules` | 最短硬约束摘要 | 与本文冲突时以 PRD + 本文为准 |
| `inventory-system.sql` | **旧基线 DDL**，不是目标模型 | 仅作迁移对照，禁止按此表结构继续加功能 |

---

## 3. 目标目录与包结构

按标准三层，按类型分包，不按业务垂直切仓：

```
src/main/java/com/dream/inventory/
  InventoryApplication.java
  common/          Result、PageResult、错误码、业务异常、trace_id
  config/          CORS、Security、JPA、Jackson、WebMvc
  entity/          JPA 实体（含关系与 @Version）
  repository/      Spring Data JPA
  service/         业务；库存写操作只允许在 InventoryService
  controller/      REST，前缀 /api/v1；Controller 不开事务
  dto/             入参/出参，不直接把 Entity 暴露给前端
  security/        JWT、UserDetails、权限常量（落地时再建）
  job/             预留超时、盘点超时、预警扫描（落地时再建）

src/main/resources/static/     前端页面（index 及各业务 HTML）
src/main/resources/application.yml
```

命名：实体用业务名（`Inventory`、`StockMovement`）；仓储 `XxxRepository`；服务 `XxxService`；控制器 `XxxController`。

---

## 4. 架构铁律（必须遵守）

1. **分层**：`Controller → Service → Repository → Entity`。Controller 只做参数校验与鉴权注解，不写库存、不开事务。
2. **单一变动入口**：`inventory` 表只允许被 `InventoryService` 的原子方法修改：`increase / reserve / release / deduct / adjust`（调拨在途用独立原子方法，不得绕开）。其它 Service 只能调用这些方法，禁止直接 `save` 库存实体或手写改数量。
3. **单据驱动**：任何库存数量变化必须挂靠已审核/可执行的单据（采购入库、销售出库、调拨、盘点调整、退货）。禁止无单改库存。
4. **同事务日志**：`inventory` 更新与 `inventory_log` 插入必须在同一个 `@Transactional(rollbackFor = Exception.class)` 中；失败整单回滚。日志只增不改不删，回滚 = 再插一条反向日志。
5. **状态机**：单据状态只能沿 PRD §4 预定义路径流转，非法跃迁返回 `ILLEGAL_STATE_TRANSITION`。
6. **统一响应**：所有 API 返回 `Result<T> { code, message, data }`；列表为 `Result<PageResult<T>>`。成功 `code = 0`。错误码见 PRD §8，不要另造一套。
7. **发起人 ≠ 审核人**：同一用户不可审核自己创建的单据（`ROLE_ADMIN` 例外，但必须写审计日志）。
8. **仓库数据范围**：`ROLE_WAREHOUSE` 必须按 `user_warehouse` 过滤；越权返回 `FORBIDDEN (40300)`。

---

## 5. 库存不变量

库存粒度是 **SKU + 仓库 + 库位**，不挂 SPU。唯一键 `(sku_id, warehouse_id, location_id)`，无库位时 `location_id = 0`。

任意时刻必须成立：

```
available_qty >= 0
reserved_qty  >= 0
on_hand_qty   >= 0
available_qty = on_hand_qty - reserved_qty
```

| 字段 | 含义 |
|---|---|
| `on_hand_qty` | 实物在库 |
| `reserved_qty` | 已预留未出库 |
| `available_qty` | 可售（冗余，便于查询与 CHECK） |
| `in_transit_qty` | 调拨在途（记在目标仓） |
| `locked` / `lock_stocktake_id` | 盘点锁定 |
| `version` | 乐观锁 |

并发策略：乐观锁为主（`@Version` + 条件 `UPDATE ... WHERE version=? AND ...`）；入库/调拨/盘点批量写可用 `SELECT ... FOR UPDATE`；数据库 CHECK 为最后防线。多行按 `sku_id` 升序更新防死锁。乐观冲突最多重试 3 次（20/40/80 ms），耗尽抛 `CONCURRENT_CONFLICT`。

销售出库两阶段：预留扣 `available`+加 `reserved`；出库扣 `on_hand`+`reserved`，**出库阶段不再改 available**。超卖只在预留阶段用 `available >= q` 判定，SQL `WHERE available_qty >= :q` 二次兜底。

盘点锁定期间：预留/出入库确认/调拨确认一律拒绝（`INVENTORY_LOCKED`）。盘亏后若 `on_hand < reserved` 拒绝自动生效。

---

## 6. 核心对象速查

- **SPU / SKU**：库存、单据明细、日志全部挂 SKU。
- **Warehouse / Location**：仓可实体/虚拟（在途仓、退货待检仓）；库位可选。
- **StockMovement + Item**：所有单据一张主表，`type` 区分 `PURCHASE_IN / SALE_OUT / SALE_RETURN / PURCHASE_RETURN / TRANSFER / ADJUST / OTHER_IN / OTHER_OUT`。单号 `{类型前缀}{yyyyMMdd}{6位序列}`。
- **InventoryLog**：唯一数量审计事实表；`change_type` 含 IN/OUT/RESERVE/RELEASE/TRANSFER_*/ADJUST_*/RETURN_*；同事务共享 `trace_id`。
- **Stocktake**：快照取 `on_hand_qty`，`reserved` 不参与差异。
- **SafetyStockRule / StockAlert**：仓库级阈值覆盖 SKU 默认值；预警在事务 `afterCommit` 后触发，另有定时扫描。

角色：`ROLE_ADMIN` / `ROLE_WAREHOUSE` / `ROLE_BIZ`。权限点形如 `purchase:approve`、`inventory:adjust`。矩阵见 PRD §2。

---

## 7. API 与前端约定

- 前缀：`/api/v1`；鉴权头 `Authorization: Bearer <JWT>`。
- 写操作（审核、入库确认、出库确认、盘点生效）请求体必须带单据 `version`，用 `WHERE id=? AND status=? AND version=?` 防重复提交，0 行 → `40900 STATE_CONFLICT`。
- 创建类接口可接受可选 `Idempotency-Key`（24h 去重）。
- 前端：单文件 HTML，Vue 3 + Element Plus CDN；提交按钮防重复点击；列表分页 `page, size, sort`。
- 时间：库内 UTC，前端按浏览器时区展示。

---

## 8. 当前代码基线（实现时必须迁移，禁止在旧模型上堆功能）

现状：几乎空壳（仅 `InventoryApplication` + 测试）。`inventory-system.sql` 仍是旧模型：

| 旧 | 目标（PRD） |
|---|---|
| `product.stock` 单字段 | `inventory (sku_id, warehouse_id, location_id)` + reserved/available/version/locked |
| `stock_in` / `stock_out` 两张表 | `stock_movement` + `stock_movement_item`，type + 状态机 |
| 无变动日志 | `inventory_log` 只增表 |
| supplier/customer 为字符串 | 独立实体表 |

落地顺序建议：`common`（Result/错误码）→ 目标 DDL/实体 → `InventoryService` 原子方法 + 日志 → 单据状态机 → 鉴权 → 前端页面。

---

## 9. Agent 工作协议

- 改库存相关代码时：先确认调用链最终进入 `InventoryService`，且日志在同一事务。
- 新增单据类型或状态：先对照 PRD 状态机，禁止发明捷径状态。
- 不要引入 Redis/消息队列（v1 明确不需要）；不要把前端改成 npm/Vite/多文件工程。
- 不要直接 UPDATE/DELETE `inventory_log`；不要为了“修数据”提供改 `inventory` 数量的后门（走 ADJUST 单据 + 管理员审批）。
- 生产 DDL 不要依赖 `ddl-auto: update`。
- 用户未要求时不主动 git commit / push。
