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

## 模块导览

| 目录 | 职责 | 状态 |
|---|---|---|
| forge-bom | 依赖版本唯一入口 | ✅ |
| forge-platform/* | 日志/异常/审计/数据(JPA 租户基线)/缓存(租户前缀)/安全(token+租户上下文)/存储技术底座 | 按模块 |
| forge-contracts/* | 跨模块接口/DTO/事件/错误码 | ✅ 示例 |
| forge-services | 身份/组织/权限/租户等业务共享能力 | 📦 Phase 2b（认证/四服务） |
| forge-apps | 具体业务应用 | 📦 Phase 2+ |
| forge-starters | 统一自动装配：web 基线（forge-spring-boot-starter）+ 按需 forge-data-starter / forge-security-starter | ✅ |
| forge-distributions | 应用组装（先看 forge-demo-app） | ✅ Demo（连库多租户） |
| forge-template | 新业务模块脚手架 | 📦 Phase 5 |

文档：`docs/architecture`（依赖规则）· `docs/development`（上手）· `docs/database`（Flyway）· `docs/standards`（错误码/编码规范）

## 技术栈

JDK 25 · Spring Boot 4.1.x · Spring Modulith 2.1.x · PostgreSQL 18 + Flyway · Redis 8 · Spring Security（token 认证）· Maven（wrapper）· 无 Lombok
