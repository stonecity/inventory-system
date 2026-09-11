# 库存管理系统 (IMS)

面向中小企业的多仓库商品库存管理系统。采用 **Spring Boot 三层架构** + **Spring Data JPA** + **MySQL**，前端为单文件 HTML + Vue 3 (CDN) + Element Plus (CDN)。

## 功能概览

| 模块 | 状态 | 说明 |
|------|------|------|
| 用户鉴权 | ✅ 已实现 | JWT + RBAC，三种角色（管理员 / 仓库 / 业务） |
| 用户 / 角色权限 | ✅ 已实现 | 用户管理、角色权限树 |
| 商品分类 | ✅ 已实现 | 树形分类 CRUD |
| 商品 SPU / SKU | ✅ 已实现 | 商品与规格 SKU 管理 |
| 仓库 / 库位 | ✅ 已实现 | 多仓库、库位批量创建 |
| 供应商 / 客户 | ✅ 已实现 | 独立主数据维护 |
| 采购入库 | ✅ 已实现 | 单据驱动，审核后验货入库 |
| 销售出库 | ✅ 已实现 | 两阶段预留 + 出库，超时自动释放 |
| 退货 | ✅ 已实现 | 销售退货入库、采购退货出库 |
| 调拨 | ✅ 已实现 | 调出在途、调入确认、在途取消 |
| 盘点 | ✅ 已实现 | 锁定快照、实盘、审批生成调整单 |
| 库存查询 / 日志 | ✅ 已实现 | 实时库存、变动日志导出、盘点期解锁 |
| 库存预警 | ✅ 已实现 | 安全库存阈值、定时扫描、生成采购单 |
| 仪表盘 / 报表 | ✅ 已实现 | 概览、趋势、Top SKU、出入库汇总、库龄 |

## 技术栈

| 层 | 选型 |
|---|---|
| 语言 | Java 17 |
| 框架 | Spring Boot 4.1.1 |
| 持久化 | Spring Data JPA + MySQL 8.0.16+ |
| 安全 | Spring Security + JWT (jjwt 0.12.6) |
| 实体 | Lombok |
| 前端 | Vue 3 + Element Plus + Tailwind CSS (CDN) |

## 项目结构

```
inventory_system/
├── Dockerfile
├── docker-compose.yml
├── .env.example
├── docker/
│   └── maven-settings.xml   # 可选：国内 Maven 镜像
├── docs/
│   ├── CONTEXT.md      # 架构铁律与开发约定
│   ├── PRD.md          # 完整需求规格（状态机、API、错误码）
│   └── schema.sql      # 目标库 DDL（生产 / Docker 首次初始化）
├── src/main/java/com/dream/inventory/
│   ├── common/         # Result、PageResult、错误码、全局异常
│   ├── config/         # Security、CORS、数据初始化
│   ├── controller/     # REST API（/api/v1）
│   ├── dto/            # 请求/响应 DTO
│   ├── entity/         # JPA 实体
│   ├── repository/     # Spring Data JPA
│   ├── job/            # 预留超时、盘点超时
│   ├── security/       # JWT 过滤器、UserDetails
│   └── service/        # 业务逻辑
├── src/main/resources/
│   ├── application.yml
│   ├── application-docker.yml
│   └── static/         # 前端页面（login、index、categories、skus、warehouses）
└── pom.xml
```

## 快速开始

### Docker 部署

需要本机已安装 [Docker Desktop](https://docs.docker.com/desktop/)（含 Docker Compose）。

```bash
# 可选：复制环境变量并修改数据库口令、JWT Secret
# Windows: copy .env.example .env
# Linux / macOS: cp .env.example .env

docker compose up -d --build
```

| 项 | 地址 / 说明 |
|---|---|
| 应用 | http://localhost:8080/login.html |
| MySQL | `localhost:3306`，库名 `inventory_system` |
| 默认账号 | `admin` / `admin123` |

首次启动会用 `docs/schema.sql` 建库建表（含 CHECK 约束与角色权限种子），应用以 `docker` profile 连接容器内 MySQL，`ddl-auto=validate`。管理员账号仍由应用启动时的 `DataInitializer` 写入。

常用命令：

```bash
docker compose logs -f app
docker compose ps
docker compose down          # 停服务，保留数据卷
docker compose down -v       # 停服务并清空 MySQL 数据（会重新执行 schema.sql）
```

导入测试数据：请在**应用首次启动前**对空库执行，或先 `docker compose down -v` 后只启动 MySQL 再导入，否则会与自动创建的 `admin` 冲突。口令需与 `.env` 一致；测试账号密码均为 `123456`。

```bash
docker compose up -d mysql
docker compose exec -T mysql mysql -uims -pims123456 inventory_system < docs/seed-test-data.sql
docker compose up -d --build
```

生产环境请修改 `.env` 中的 `MYSQL_ROOT_PASSWORD`、`MYSQL_PASSWORD`、`IMS_JWT_SECRET`。MySQL 默认只绑定本机 `127.0.0.1:3306`。国内构建若拉取 Maven 依赖较慢，可在 `Dockerfile` 中取消注释阿里云镜像那一行。

### 环境要求（本地运行）

- JDK 17+
- Maven 3.8+
- MySQL 8.0.16+

### 1. 创建数据库

**开发环境**（JPA 自动建表）：

```sql
CREATE DATABASE inventory_system
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
```

**生产环境**（推荐）：

```bash
mysql -u root -p < docs/schema.sql
```

### 2. 配置数据库

编辑 `src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/inventory_system?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
    username: root
    password: your_password
```

### 3. 启动应用

```bash
mvn spring-boot:run
```

应用默认运行在 `http://localhost:8080`。

### 4. 访问前端

| 页面 | 地址 |
|------|------|
| 登录 | http://localhost:8080/login.html |
| 首页 | http://localhost:8080/index.html |
| 库存查询 | http://localhost:8080/inventory.html |
| 出入库单据 | http://localhost:8080/movements.html |
| 盘点 | http://localhost:8080/stocktake.html |
| 预警 | http://localhost:8080/alerts.html |
| 报表 | http://localhost:8080/reports.html |
| 商品 SPU / SKU | http://localhost:8080/products.html 、 skus.html |
| 分类 / 仓库 | http://localhost:8080/categories.html 、 warehouses.html |
| 供应商 / 客户 | http://localhost:8080/suppliers.html 、 customers.html |
| 安全库存 | http://localhost:8080/safety-stock.html |
| 用户 / 角色 | http://localhost:8080/users.html 、 roles.html |
| 系统设置 | http://localhost:8080/settings.html |

### 5. 默认账号

首次启动会自动初始化权限、角色与管理员账号：

| 用户名 | 密码 | 角色 |
|--------|------|------|
| `admin` | `admin123` | 超级管理员 (ROLE_ADMIN) |

> 生产环境请务必修改默认密码与 JWT Secret（`ims.jwt.secret`）。

## API 概览

所有接口前缀为 `/api/v1`，统一响应格式：

```json
{
  "code": 0,
  "message": "success",
  "data": { }
}
```

成功时 `code = 0`；错误码定义见 `docs/PRD.md` 第 8 节。

### 鉴权

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/auth/login` | 登录，返回 JWT |
| POST | `/api/v1/auth/logout` | 退出 |
| GET | `/api/v1/auth/me` | 当前用户信息 |

除登录外，请求头需携带：`Authorization: Bearer <token>`

### 基础数据

| 方法 | 路径 | 说明 |
|------|------|------|
| GET/POST/PUT/DELETE | `/api/v1/categories` | 商品分类 |
| GET/POST/PUT | `/api/v1/products` | 商品 SPU |
| GET/POST/PUT | `/api/v1/skus` | SKU 管理 |
| GET/POST/PUT | `/api/v1/warehouses` | 仓库管理 |
| GET/POST/PUT | `/api/v1/warehouses/{id}/locations` | 库位管理 |

## 架构要点

- **分层**：`Controller → Service → Repository → Entity`
- **单一库存写入口**：`inventory` 表仅允许 `InventoryService` 的原子方法修改（`increase / reserve / release / deduct / adjust`）
- **单据驱动**：所有库存变动必须挂靠已审核单据
- **同事务日志**：库存更新与 `inventory_log` 写入在同一事务中
- **库存不变量**：`available_qty = on_hand_qty - reserved_qty`，三者均 ≥ 0

详细设计见 [docs/CONTEXT.md](docs/CONTEXT.md) 与 [docs/PRD.md](docs/PRD.md)。

## 角色与权限

| 角色 | 编码 | 职责 |
|------|------|------|
| 超级管理员 | `ROLE_ADMIN` | 系统配置、用户权限、最终审批 |
| 仓库管理员 | `ROLE_WAREHOUSE` | 所辖仓库验货、拣货、盘点、调拨 |
| 采购/销售 | `ROLE_BIZ` | 发起采购/销售单据，维护供应商客户 |

## 开发说明

```bash
# 编译
mvn compile

# 运行测试
mvn test

# 打包
mvn package -DskipTests
```

- 开发环境可使用 `jpa.hibernate.ddl-auto: update` 自动同步表结构
- 生产环境应使用 `validate` + Flyway/Liquibase，DDL 以 `docs/schema.sql` 为准
- v1 不引入 Redis、消息队列
- 旧基线 `inventory-system.sql` 已废弃，禁止在其表结构上继续开发

## License

MIT
