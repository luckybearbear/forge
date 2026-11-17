# forge-demo-app

可运行 Demo：mold + maintenance 双业务模块，演示契约同步查询（MoldQueryService）+ 领域事件
（MoldRepairRequested：@ApplicationModuleListener 事务提交后异步投递 + event_publication 持久化）
+ Modulith 边界强制 + 连库多租户（JPA 判别式隔离、审计、乐观锁、软删除、Flyway）
+ **认证全链路（Task 8：四服务 tenant/identity/organization/authorization + security/cache/audit
三 starter 首次同台组装）**——登录 token 驱动的租户上下文、方法级权限（@PreAuthorize）、
权限快照缓存、访问/错误审计日志。

## 连库运行（PostgreSQL + Redis）

连接默认 `jdbc:postgresql://localhost:5432/forge_demo`，凭据默认 `forge`/`forge`（与根 compose 的
postgres 服务三元组一致），以环境变量覆盖：`FORGE_DB_URL` / `FORGE_DB_USERNAME` /
`FORGE_DB_PASSWORD`。业务表由 Flyway（`db/migration`，demo 自身 + 四服务/审计 starter jar 内迁移）
初始化；`event_publication`（Modulith 事件登记表）由 `spring.modulith.events.jdbc.schema-initialization.enabled=true`
自动建表；JPA `ddl-auto=validate` 仅校验映射、不建表。**Redis 必备**（security 的 token 存储 +
authorization 的权限快照缓存），连接走 Boot 缺省 `localhost:6379`（与 compose 的 redis 服务一致，
异地部署经 `spring.data.redis.host/port` 覆盖）。

本地基础设施二选一：

1. compose（含 Redis，认证链依赖）：仓库根 `docker compose --profile infra up -d`
   （PG 5432 / Redis 6379，库 forge / 用户 forge / 密码 forge），再补建 demo 库后启动
   （默认凭据即 forge/forge，无需再传 FORGE_DB_USERNAME/FORGE_DB_PASSWORD）：

   ```bash
   docker exec -it $(docker ps -qf name=postgres) psql -U forge -d forge \
     -c "CREATE DATABASE forge_demo"
   JAVA_HOME=<jdk25> ./mvnw -pl forge-distributions/forge-demo-app -am spring-boot:run
   ```

2. 常驻 PostgreSQL（本机已占 5432 的实例）：建独立库 `forge_demo` 后以环境变量指向，
   例如（仅示例，勿写入任何配置文件）：
   `FORGE_DB_URL=jdbc:postgresql://localhost:5432/forge_demo FORGE_DB_USERNAME=postgres FORGE_DB_PASSWORD=change-me`
   （`postgres`/`change-me` 替换为本机实例真实凭据；真实凭据不进版本库）

## 认证体验流（Task 8）

1. **种子数据**：以 `demo` profile 启动即铺底（`SPRING_PROFILES_ACTIVE=demo`，测试上下文用 `test`
   同一份；`DemoDataSeeder` 经服务 API 创建，幂等可重启）：

   | 租户 | 编码 | 用户 | 口令 | 角色 |
   |------|------|------|------|------|
   | 精密制造 | t-mfg | mfg-admin | Admin@123 | ADMIN（全权限） |
   | 模具车间 | t-mold | mold-admin | Admin@123 | ADMIN（全权限） |
   | 模具车间 | t-mold | mold-viewer | Viewer@123 | VIEWER（仅 mold:read） |

2. **登录**：`POST /app-api/auth/login`，体 `{"tenantCode":"t-mfg","username":"mfg-admin",
   "password":"Admin@123"}` → `data.token`。
3. **Swagger**：打开 `/swagger-ui/index.html` → 右上 **Authorize** → 值填 `Bearer <token>`
   （注意携带 `Bearer ` 前缀）。
4. **业务端点**：`POST /app-api/molds`（需 mold:create）→ `GET /app-api/molds/{id}`（mold:read）→
   `POST /app-api/molds/{id}/repairs`（mold:repair）→ `GET /app-api/maintenances?moldId={id}`
   （maint:read）。mold-viewer 登录后 POST → 403 FORGE-0403（方法级权限）。
   组织面：`GET /app-api/org-units/tree` 等三控制器类级门禁 `org:manage`（mold-viewer 403，ADMIN 有）。
   管理：`GET/PUT /app-api/roles/**`（需 role:manage，ADMIN 有）。

> **权限快照语义**：权限集在登录时冻结进 TokenGrant——给角色加/减权限后**旧 token 不变**
> （越权依旧 403），**重新登录**即取新集（authorization 侧快照缓存已被写路径显式 evict）。
> token 内权限刷新机制属 Phase 3。双租户互不可见、登出吊销、审计落表、org 面门禁等九场景验收见
> `DemoAuthFlowIntegrationTest`（Testcontainers 真实 PG/Redis 全 HTTP 链）。

> **OSIV 关闭**（application.yaml `spring.jpa.open-in-view=false`）：OSIV 请求级会话在租户上下文
> 填充前开启（判别式解析为平台租户 "0"，租户表恒查空），Boot 4 + Hibernate 7.4 实证 identity
> 登录链的 REQUIRES_NEW 不足以恢复判别式 → 恒 IDENT-0401；demo 实体无跨层懒加载，关闭无副作用。

## 多租户与事件恢复（Task 11）

- 实体 extends `AbstractTenantEntity`：`@TenantId` 判别式过滤——A/B 租户互不可见、平台视角恒空，
  `DemoTenantIsolationIntegrationTest` 于 Testcontainers 真实 PG 验收（含 event_publication COMPLETED
  与物理 tenant_id 断言）；认证链下的双租户隔离经真实 token 验收（DemoAuthFlowIntegrationTest 场景 3）。
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
子包仍在模块内，verify 保持绿。四服务在根包外（io.github.luckybearbear.forge.service.*），
不影响模块划分；`DemoDataSeeder`/`ForgeDemoApplication` 栖身主包根级（Modulith 把根包每个直接
子包视为模块——config/ 子包会成为第三模块，实证裁定）。

> 注：verify() 基于 ArchUnit 字节码分析，**未被使用的 import 不留字节码痕迹**，不会被检出；
> 只有真实的类型引用（字段/参数/调用）才会触发 Violation。手工负向验证时需构造真实引用。
