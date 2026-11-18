# Forge

**A Modular Java Application Foundation for Building SaaS Products.**

模块化单体底座：platform（技术基础）/ contracts（模块契约）/ services（业务共享）/ apps（业务应用）
+ starters / distributions / bom。跨模块协作只走契约 + 领域事件，Spring Modulith 强制模块边界。

## 快速开始

前置：JDK 25（本仓库不依赖本机 Maven，wrapper 自带）；Docker——集成测试经 Testcontainers 起
真实 PG/Redis 容器，Demo 连库运行依赖 PostgreSQL（**无 Docker 时容器集成测试自动 skip**，
其余测试照常执行；环境细节见 `docs/development/getting-started.md`）。

```bash
# 全量构建 + 测试（含 Modulith 边界验证；容器集成测试需 Docker 在场）
./mvnw verify

# 基础设施（PostgreSQL 18 + Redis 8；Demo 连库与 2b 认证依赖）
docker compose --profile infra up -d

# 运行 Demo（mold + maintenance，连 PostgreSQL，默认 localhost:5432/forge_demo，
# 经 FORGE_DB_URL/FORGE_DB_USERNAME/FORGE_DB_PASSWORD 覆盖；详见 forge-distributions/forge-demo-app/README.md）
./mvnw -pl forge-distributions/forge-demo-app -am spring-boot:run
# API: http://localhost:8080/swagger-ui/index.html（app 端统一 /app-api 前缀）
```

### 认证体验（Phase 2b：登录 → token → swagger Bearer）

demo profile 启动即铺种子（两租户三账号，幂等可重启；Redis 必备——token 存储与权限快照缓存）：

```bash
SPRING_PROFILES_ACTIVE=demo ./mvnw -pl forge-distributions/forge-demo-app -am spring-boot:run
```

1. 登录：`POST /app-api/auth/login`，体 `{"tenantCode":"t-mfg","username":"mfg-admin","password":"Admin@123"}` → 取 `data.token`
2. swagger 右上 **Authorize** 填 `Bearer <token>`（含 `Bearer ` 前缀）→ 调业务端点（mold-viewer 登录后越权 POST → 403 FORGE-0403）
3. 管理面：`POST /admin-api/auth/login` 用平台管理员 `admin` / `admin123`（初始口令——**首次部署登录后立即执行 `PUT /admin-api/auth/password` 修改**，成功后既有 token 全部吊销需重登）

种子表/权限快照（变更需重登）/审计落表等细节见 `forge-distributions/forge-demo-app/README.md` 与 `docs/development/getting-started.md`。

### 前端快速开始（forge-web 管理台）

前置：Node ≥ 20.19（推荐 22）+ pnpm ≥ 10（`npm i -g pnpm@10` 或 corepack）。

```bash
# 1) 起后端（demo profile，默认 8080；种子含平台管理员 admin/admin123）
SPRING_PROFILES_ACTIVE=demo ./mvnw -pl forge-distributions/forge-demo-app -am spring-boot:run

# 2) 起前端（forge-web/ 为 pnpm workspace，与 Maven 构建互不相扰）
cd forge-web && pnpm install && pnpm dev   # admin 管理台 http://localhost:5173
```

- 登录：`admin / admin123`（初始口令——首次登录后立即经右上用户下拉改密；改密吊销既有 token 需重登）；
- 代理模式：dev 下 `/admin-api` 由 vite proxy 转发到 `http://localhost:8080`（无 CORS）。
  **后端不在 8080 时**（如被占改用 18080）：改 `forge-web/apps/admin/vite.config.ts` 的 proxy
  target，或让后端仍用 8080、前端以 `.env.local` 的 `VITE_PORT` 换端口——详见 `forge-web/README.md`；
- 全量校验：`cd forge-web && pnpm -r lint && pnpm -r build && pnpm -r test`（CI 的 web job 同款）。

## 模块导览

| 目录 | 职责 | 状态 |
|---|---|---|
| forge-bom | 依赖版本唯一入口 | ✅ |
| forge-platform/* | 日志/异常/审计/数据(JPA 租户基线)/缓存(租户前缀)/安全(token+租户上下文)/存储技术底座 | 按模块 |
| forge-contracts/* | 跨模块接口/DTO/事件/错误码 | ✅ 示例 |
| forge-services | 业务共享服务：tenant（租户/套餐）/ identity（双端认证/用户）/ organization（组织树/岗位）/ authorization（角色/权限+菜单合一模型）——自动配置库形态，引依赖即得 bean/建表/路由 | ✅ 四服务（2b） |
| forge-apps | 具体业务应用 | 📦 Phase 2+ |
| forge-starters | 统一自动装配：web 基线（forge-spring-boot-starter）+ 按需 data / security / cache starter + 组合型 audit starter（访问/错误日志落库） | ✅ |
| forge-distributions | 应用组装（先看 forge-demo-app） | ✅ Demo（连库多租户） |
| forge-web | 前端 web 系（pnpm workspace）：apps/admin 管理台（登录/动态路由/权限指令/租户/权限字典）+ packages/shared 请求层与类型 | ✅ 基座（Phase 3） |
| forge-mobile | 移动端占位（独立 uni-app 工程，不共 workspace——理由见其 README） | 📦 占位 |
| forge-template | 新业务模块脚手架 | 📦 Phase 5 |

文档：`docs/architecture`（依赖规则）· `docs/development`（上手）· `docs/database`（Flyway）· `docs/standards`（错误码/编码规范）

## 技术栈

JDK 25 · Spring Boot 4.1.x · Spring Modulith 2.1.x · PostgreSQL 18 + Flyway · Redis 8 · Spring Security（token 认证）· Maven（wrapper）· 无 Lombok
前端（forge-web）：Node 22/pnpm 10 · Vue 3.5 + TS + Vite 8 · element-plus · pinia 3 · vitest/eslint(flat)/prettier
