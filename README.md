# Forge

**A Modular Java Application Foundation for Building SaaS Products.**

模块化单体底座：platform（技术基础）/ contracts（模块契约）/ services（业务共享）/ apps（业务应用）
+ starters / distributions / bom。跨模块协作只走契约 + 领域事件，Spring Modulith 强制模块边界。

## 快速开始

前置：JDK 25（本仓库不依赖本机 Maven，wrapper 自带）。

```bash
# 全量构建 + 测试（含 Modulith 边界验证）
./mvnw verify

# 运行 Demo（mold + maintenance，内存存储零 DB）
./mvnw -pl forge-distributions/forge-demo-app spring-boot:run
# API: http://localhost:8080/swagger-ui/index.html

# 可选基础设施（PostgreSQL 18 + Redis 8，Phase 2 起需要）
docker compose --profile infra up -d
```

## 模块导览

| 目录 | 职责 | 状态 |
|---|---|---|
| forge-bom | 依赖版本唯一入口 | ✅ |
| forge-platform/* | 日志/异常/审计/数据/缓存/安全/存储技术底座 | 按模块 |
| forge-contracts/* | 跨模块接口/DTO/事件/错误码 | ✅ 示例 |
| forge-services | 身份/组织/权限/租户等业务共享能力 | 📦 Phase 2 |
| forge-apps | 具体业务应用 | 📦 Phase 2+ |
| forge-starters | 统一自动装配 | ✅ |
| forge-distributions | 应用组装（先看 forge-demo-app） | ✅ Demo |

文档：`docs/architecture`（依赖规则）· `docs/development`（上手）· `docs/database`（Flyway）· `docs/standards`（错误码/编码规范）

## 技术栈

JDK 25 · Spring Boot 4.1.x · Spring Modulith 2.1.x · PostgreSQL（Phase 2）· Maven（wrapper）· 无 Lombok
