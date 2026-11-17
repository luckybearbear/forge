# forge-demo-app

可运行 Demo：mold + maintenance 双业务模块，演示契约同步查询（MoldQueryService）+ 领域事件
（MoldRepairRequested：@ApplicationModuleListener 事务提交后异步投递 + event_publication 持久化）
+ Modulith 边界强制 + 连库多租户（JPA 判别式隔离、审计、乐观锁、软删除、Flyway）。

## 连库运行（PostgreSQL）

连接默认 `jdbc:postgresql://localhost:5432/forge_demo`（用户 `postgres`），以环境变量覆盖：
`FORGE_DB_URL` / `FORGE_DB_USERNAME` / `FORGE_DB_PASSWORD`。业务表由 Flyway（`db/migration`）初始化；
`event_publication`（Modulith 事件登记表）由 `spring.modulith.events.jdbc.schema-initialization.enabled=true`
自动建表；JPA `ddl-auto=validate` 仅校验映射、不建表。

本地基础设施二选一：

1. compose（含 Redis，供 2b 认证接入）：仓库根 `docker compose --profile infra up -d`
   （PG 5432 / Redis 6379，库 forge / 用户 forge / 密码 forge），再补建 demo 库后启动：

   ```bash
   docker exec -it $(docker ps -qf name=postgres) psql -U forge -d forge \
     -c "CREATE DATABASE forge_demo"
   JAVA_HOME=<jdk25> FORGE_DB_USERNAME=forge FORGE_DB_PASSWORD=forge \
     ./mvnw -pl forge-distributions/forge-demo-app -am spring-boot:run
   ```

2. 常驻 PostgreSQL（本机已占 5432 的实例）：建独立库 `forge_demo` 后以环境变量指向，
   例如（仅示例，勿写入任何配置文件）：
   `FORGE_DB_URL=jdbc:postgresql://localhost:5432/forge_demo FORGE_DB_USERNAME=postgres FORGE_DB_PASSWORD=change-me`
   （`change-me` 替换为本机实例真实口令；真实凭据不进版本库）

体验流（controller 迁入 `controller/app` 包 → platform-web 双端前缀，app 端统一 `/app-api`）：
`POST /app-api/molds` → `GET /app-api/molds/{id}` → `POST /app-api/molds/{id}/repairs` →
`GET /app-api/maintenances?moldId={id}`；API 文档 `/swagger-ui/index.html`；健康 `/actuator/health`。

> 认证预告（2b）：报修发布事件要求租户上下文（`TenantContextHolder.getRequiredTenantId()`），
> 接入 forge-security-starter 的 TokenAuthFilter 后由登录 token 自动填充并携带进事件 payload。
> 当前本地裸跑登记/查询可用（平台视角），完整报修流由集成测试（X-Tenant-Id 测试过滤器）覆盖。

## 多租户与事件恢复（Task 11）

- 实体 extends `AbstractTenantEntity`：`@TenantId` 判别式过滤——A/B 租户互不可见、平台视角恒空，
  `DemoTenantIsolationIntegrationTest` 于 Testcontainers 真实 PG 验收（含 event_publication COMPLETED
  与物理 tenant_id 断言）；审计列/乐观锁/软删除（Hibernate 7 类级 `@SoftDelete`）由 forge-data-starter
  基类机制承载，实体与 SQL 不写 deleted 之外的任何特殊处理。
- `MoldRepairRequested` 契约事件携带 `tenantId`（Phase 2a breaking 变更）：异步监听线程不继承 TTL
  上下文，监听器入口显式恢复 / finally 清理（spec §6.1）；业务落库方法 `REQUIRES_NEW` 自开事务——
  判别式租户在 Hibernate 会话开启时解析，须确保会话在租户恢复后开启（listener 隐式事务在方法体
  之前开 session，实证见 MaintenanceService#createFromEvent 注释）。
- Phase 1 偏差（同步 @EventListener 降级）已关闭：恢复 `@ApplicationModuleListener`
  （事务提交后异步投递 + 登记表完成标记 + 失败重投，spec §10）。

## 模块边界

`ModulithBoundaryTest`（`ApplicationModules.of(ForgeDemoApplication.class).verify()`）守护：
maintenance 禁止 import `io.github.luckybearbear.forge.demo.mold.**` 内部类型，跨模块只走
forge-contract-mold 契约（DTO / 事件 / 查询接口 / 错误码）；controller 迁入 `controller/app`
子包仍在模块内，verify 保持绿。

> 注：verify() 基于 ArchUnit 字节码分析，**未被使用的 import 不留字节码痕迹**，不会被检出；
> 只有真实的类型引用（字段/参数/调用）才会触发 Violation。手工负向验证时需构造真实引用。
