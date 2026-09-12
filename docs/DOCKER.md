# IMS Docker 打包部署操作文档

本文说明如何把库存管理系统（IMS）**打包成镜像并部署到 Docker**，以及如何改端口、验收和日常运维。

相关文件：

| 文件 | 作用 |
|---|---|
| `Dockerfile` | 多阶段构建：Maven 打包 → JRE 运行 fat jar |
| `docker-compose.yml` | 编排 `mysql` + `app` |
| `.env.example` | 环境变量模板 |
| `.env` | 本机实际配置（compose 自动读取，不要提交密钥） |
| `src/main/resources/application-docker.yml` | 容器内 `docker` profile |
| `docs/schema.sql` | 空数据卷首次初始化 DDL |
| `docs/seed-test-data.sql` | 空数据卷首次初始化测试数据 |

---

## 1. 架构与端口

Compose 项目名为 `ims`，两个服务：

```
宿主机                         Docker 网络
--------                       -------------
浏览器 :8080  ──────────────►  ims-app:8080
本机客户端 :3307 ────────────►  ims-mysql:3306
                               ims-app ──(mysql:3306)──► ims-mysql
```

| 用途 | 端口 | 说明 |
|---|---|---|
| 浏览器访问应用 | 宿主机 `8080` | 对应容器内 `8080`，由 `APP_PORT` 控制 |
| 宿主机连 Docker MySQL | 宿主机 **`3307`** | 映射到容器内 `3306`，由 `MYSQL_PORT` 控制 |
| 应用连 MySQL | 容器内 **`3306`** | 走 Compose 内部 DNS 主机名 `mysql`，不要改成 3307 |

**不要把应用环境变量 `MYSQL_PORT` 改成 3307。** 那是容器内部端口，MySQL 进程仍监听 3306。3307 只给宿主机上的客户端（Navicat、DBeaver、本地 `mysql` 命令）使用。

MySQL 只绑定本机回环：`127.0.0.1:3307`，局域网其它机器默认连不上。

---

## 2. 环境要求

- 已安装 [Docker Desktop](https://docs.docker.com/desktop/)（含 Docker Compose v2）
- 磁盘预留约 2 GB（Maven 构建层 + MySQL 镜像）
- 本机 **3307、8080** 未被占用（可在 `.env` 改）
- 拉不到 Docker Hub 时，在 `.env` 设置 `DOCKER_HUB=docker.m.daocloud.io/library`（DaoCloud 公共镜像）

检查：

```bash
docker version
docker compose version
```

---

## 3. 配置环境变量

在项目根目录：

```bat
REM Windows
copy .env.example .env
```

```bash
# Linux / macOS
cp .env.example .env
```

编辑 `.env`，Docker 部署建议至少确认下面几项（与当前 `docker-compose.yml` 一致：MySQL 会创建业务用户 `MYSQL_USER`）：

```env
MYSQL_ROOT_PASSWORD=请改成强密码
MYSQL_DATABASE=inventory_system
MYSQL_USER=ims
MYSQL_PASSWORD=请改成业务用户密码
MYSQL_PORT=3307

APP_PORT=8080

# 拉不到 Docker Hub 时使用 DaoCloud 公共镜像（compose 默认已是该值）
DOCKER_HUB=docker.m.daocloud.io/library

IMS_JWT_SECRET=请改成至少32字符的随机串
IMS_JWT_EXPIRATION_MS=86400000

# 首次已由 schema.sql 建表时用 validate；仅空库且未执行 schema 时才用 update
JPA_DDL_AUTO=validate
```

注意：

- `MYSQL_USER` 不能是 `root`（官方 MySQL 镜像限制）。应用走 `ims`，root 仅用于运维。
- `IMS_JWT_SECRET` 生产环境必须替换；长度不足会导致 JWT 无法签发。
- 改端口只改 `.env` 的 `MYSQL_PORT` / `APP_PORT`，然后执行第 7 节「重建端口映射」。

---

## 4. 完整打包部署流程（首次）

在项目根目录执行。

### 4.1 构建镜像并启动

```bash
docker compose up -d --build
```

该命令会：

1. 按 `Dockerfile` 多阶段构建应用镜像 `ims-app:0.0.1`
   - 构建阶段：`maven:3.9-eclipse-temurin-17` 执行 `mvn -B -DskipTests package`
   - 运行阶段：`eclipse-temurin:17-jre-jammy` 只保留 `/app/app.jar`
2. 拉取并启动 `mysql:8.0`（容器名 `ims-mysql`）
3. 若数据卷 `ims_mysql_data` 为空，依次执行：
   - `docs/schema.sql`（建库建表、CHECK、角色权限种子）
   - `docs/seed-test-data.sql`（测试数据）
4. 等 MySQL healthcheck 通过后启动 `ims-app`
5. 应用使用 Spring profile `docker`，连接主机名 `mysql`、端口 `3306`

首次构建会下载 Maven 依赖，可能需要数分钟。`Dockerfile` 已默认使用阿里云 Maven 镜像（`docker/maven-settings.xml`）。

### 4.2 查看状态

```bash
docker compose ps
```

期望类似：

```text
NAME        IMAGE           STATUS                    PORTS
ims-app     ims-app:0.0.1   Up ... (healthy)          0.0.0.0:8080->8080/tcp
ims-mysql   mysql:8.0       Up ... (healthy)          127.0.0.1:3307->3306/tcp
```

应用 healthcheck 的 `start-period` 为 90 秒，刚启动时可能显示 `health: starting`，属正常。

---

## 5. 验收：服务是否可访问

按顺序检查。

### 5.1 端口映射

```bash
docker port ims-mysql
docker port ims-app
```

期望：

```text
3306/tcp -> 127.0.0.1:3307
8080/tcp -> 0.0.0.0:8080
```

### 5.2 MySQL（容器内）

```bash
docker compose exec mysql mysqladmin ping -h 127.0.0.1 -uroot -p你的ROOT密码 --silent
```

成功会输出 `mysqld is alive`。

### 5.3 MySQL（宿主机 3307）

本机客户端连接参数：

| 项 | 值 |
|---|---|
| Host | `127.0.0.1` |
| Port | **3307** |
| User | `.env` 中的 `MYSQL_USER`（一般为 `root`） |
| Password | `.env` 中的 `MYSQL_PASSWORD` |
| Database | `inventory_system` |

PowerShell 探测端口：

```powershell
Test-NetConnection -ComputerName 127.0.0.1 -Port 3307
```

`TcpTestSucceeded` 应为 `True`。

### 5.4 应用 HTTP

Windows 请用 `curl.exe`（避免被 PowerShell 的 `curl` 别名干扰）：

```bash
curl.exe -s -o NUL -w "HTTP %{http_code}\n" http://127.0.0.1:8080/login.html
```

期望 `HTTP 200`。浏览器打开：

[http://localhost:8080/login.html](http://localhost:8080/login.html)

默认管理员：`admin` / `admin123`（若已导入测试数据，测试账号密码均为 `123456`）。

未登录访问受保护接口应返回 401，例如：

```bash
curl.exe -s -o NUL -w "HTTP %{http_code}\n" http://127.0.0.1:8080/api/v1/auth/me
```

### 5.5 看日志（启动失败时）

```bash
docker compose logs -f app
docker compose logs --tail 80 mysql
```

应用成功标志大致为 Tomcat 监听 8080、数据源初始化完成、无反复 `Communications link failure`。

---

## 6. 代码变更后重新打包

只改了 Java / 前端静态资源 / `pom.xml`：

```bash
docker compose up -d --build app
```

只改了 `.env` 或 `docker-compose.yml`（含端口），**不必重新 Maven 打包**，见下一节。

镜像 tag 在 compose 中为 `ims-app:0.0.1`，与 `pom.xml` 的 `0.0.1-SNAPSHOT` 对应。发新版本时同步改这两处。

---

## 7. 重建端口映射（改 MYSQL_PORT / APP_PORT 后）

1. 修改 `.env`，例如 `MYSQL_PORT=3307`
2. 重建容器（会保留 named volume 里的数据）：

```bash
docker compose up -d --force-recreate
```

3. 用第 5 节命令确认 `127.0.0.1:3307->3306` 以及登录页可打开

不要执行 `docker compose down -v`，否则 MySQL 数据会被清空。

---

## 8. 测试数据

当前 `docker-compose.yml` 已把 `docs/seed-test-data.sql` 挂到 MySQL 初始化目录。**仅在数据卷第一次创建时执行**；之后重启不会重复导入。

若库已经初始化过、需要重新导入：

```bash
docker compose down -v
docker compose up -d --build
```

这会删掉全部业务数据后按 schema + seed 重建。

手动导入（口令必须与 `.env` 一致；空库、应用尚未写入 `admin` 时再执行）：

```bash
docker compose up -d mysql
docker compose exec -T mysql mysql -uroot -p你的ROOT密码 inventory_system < docs/seed-test-data.sql
docker compose up -d --build
```

Windows PowerShell 的 `<` 重定向可能不可用，可改：

```powershell
Get-Content docs/seed-test-data.sql -Raw | docker compose exec -T mysql mysql -uroot -p你的ROOT密码 inventory_system
```

---

## 9. 常用运维命令

```bash
# 跟踪应用日志
docker compose logs -f app

# 进入 MySQL 容器
docker compose exec mysql mysql -uroot -p -D inventory_system

# 停止服务，保留数据
docker compose down

# 停止并删除数据卷（库会按 schema.sql / seed 重新初始化）
docker compose down -v

# 只重启应用，不重建镜像
docker compose restart app
```

数据卷名称：`ims_mysql_data`（项目名 `ims` + volume 名 `mysql_data`）。

---

## 10. 生产环境检查清单

- [ ] `.env` 中 `MYSQL_ROOT_PASSWORD`、`MYSQL_PASSWORD`、`IMS_JWT_SECRET` 已换成高强度随机值
- [ ] `JPA_DDL_AUTO=validate`（禁止生产 `update`）
- [ ] 确认宿主机防火墙与 `127.0.0.1` 绑定是否符合暴露策略
- [ ] 已备份 `ims_mysql_data` 或定期 `mysqldump`
- [ ] 不要把 `.env`、密钥提交进 Git

备份示例：

```bash
docker compose exec -T mysql mysqldump -uroot -p你的ROOT密码 --databases inventory_system > backup.sql
```

---

## 11. 故障排查

| 现象 | 可能原因 | 处理 |
|---|---|---|
| `Bind for 0.0.0.0:8080 failed` / `3307` 占用 | 本机端口冲突 | 改 `.env` 的 `APP_PORT` / `MYSQL_PORT` 后 `--force-recreate` |
| 应用一直 `Communications link failure` | MySQL 未就绪或账号不对 | `docker compose ps` 看 mysql 是否 healthy；确认 `.env` 的 `MYSQL_USER` / `MYSQL_PASSWORD` 与 compose 一致 |
| 登录页 200，登录失败 | 数据未初始化或口令被 seed 覆盖 | 看 `DataInitializer` / seed；必要时 `down -v` 后重来 |
| 改了代码容器没变 | 未重新构建镜像 | `docker compose up -d --build app` |
| 首次 Maven 极慢 / 失败 | 依赖拉取超时 | 确认 `Dockerfile` 已 COPY `docker/maven-settings.xml`（阿里云镜像） |
| 宿主机 `3306` 连不上 Docker 库 | 映射已改为 3307 | 客户端改连 `127.0.0.1:3307` |
| `schema.sql` 改了但库结构没变 | 初始化脚本只在空卷执行一次 | 开发环境可 `down -v`；生产应走迁移，不要依赖 init 脚本改已有库 |

---

## 12. 与本地 IDE 运行的区别

| | Docker | 本地 `mvn spring-boot:run` |
|---|---|---|
| 配置文件 | `application-docker.yml` | `application.yml` |
| 数据库地址 | 容器主机名 `mysql:3306` | 默认 `localhost:3306`（本机 MySQL） |
| 连 Docker 里的库 | 本机客户端用 `localhost:3307` | 把 `application.yml` 端口改成 `3307` 即可连同一套库 |

本地开发若要连 Docker MySQL，只需把 `application.yml` 的 JDBC URL 端口改为 **3307**，账号与 `.env` 一致。不要改 `application-docker.yml` 的默认 3306。
