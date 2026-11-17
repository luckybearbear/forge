# forge-service-tenant

租户/套餐服务——**Phase 2b "服务=自动配置库" 形态的范式样板**（identity/organization/authorization 服务照抄本模块形态）。

## 模块形态（样板要点，T4-6 复用清单）

- **pom**：parent `forge-services`；依赖 `forge-data-starter` + `forge-spring-boot-starter` + `forge-contract-tenant`
  （platform-core 经契约传递）；test：`spring-boot-starter-test` + `forge-platform-test`。无 Lombok。
- **自动配置**：`TenantServiceAutoConfiguration` = `@AutoConfiguration` + `@ComponentScan(模块根包)` +
  `@EntityScan(domain)` + `@EnableJpaRepositories(domain)`；无 `@Bean` 方法——组装全经组件扫描
  （`app/` 服务、`controller/admin/` 控制器、模块根包契约实现），覆盖治理点为整模块（不引本依赖即整体让位）。
  - **@AutoConfiguration 类本身不能被 @ComponentScan 拾取**：本类的 @ComponentScan 显式挂
    `AutoConfigurationExcludeFilter`（自扫排除自身）；消费方 `@SpringBootApplication` 扫描自带同一
    filter，配合 `META-INF/spring/...AutoConfiguration.imports`（一行 FQN）即自动生效——双路径均不重复注册。
  - **Boot 4 注意**：`@EntityScan` 在新包 `org.springframework.boot.persistence.autoconfigure`
    （spring-boot-persistence，经 starter-data-jpa 传递）。
- **包布局**：`domain/`（实体+仓库，全局表）· `app/`（应用服务）· `controller/admin/`（管理端 REST +
  请求 DTO record）· 模块根包（`TenantServiceAutoConfiguration` + 契约实现 `TenantQueryServiceImpl`）。
- **Flyway**：迁移随 jar 走（`src/main/resources/db/migration/`），消费应用引本依赖即建表。
- **路由**：控制器在 `controller.admin.*` 包 → platform-web 双端前缀自动前插 `/admin-api`（spec §6.5）。

## 域语义

- **全局表**：`tenant`/`tenant_package` extends `AbstractAuditableEntity`（非 `AbstractTenantEntity`）——
  无 `@TenantId`，判别式过滤/平台写守护均不适用（写守护防的是租户表 `tenant_id='0'` 黑洞）；
  软删除/审计/乐观锁/UUIDv7 主键由基类机制承载。
- **code 唯一**：partial unique index `WHERE deleted = FALSE`——软删行不占码，编码可复用（服务层先查后插 +
  DB 索引兜底并发）。
- **状态机**：创建即 `INIT`（enable 显式开通）→ `ENABLED` ↔ `DISABLED`。
  - `disable` **幂等**：已 DISABLED 返回当前状态、**不重复发** `TenantDisabled` 事件（按事件触发的下游清理不放大）。
  - `enable` 幂等：任意状态 → ENABLED。
- **update 部分更新**：null 字段不改（name/packageId/expiredAt；套餐同理）。
- **package_id 无物理 FK**：套餐可先行软删、租户侧重绑；本任务不做套餐存在性校验（无专属错误码段，见下）。
- **事件**（ApplicationEventPublisher，业务事务内发布，spec §10）：`TenantCreated`（create 时）、
  `TenantDisabled`（disable 且状态实际迁移时）。tenant 域为全局表——事件 payload 的 `tenantId` 是
  **事件主体租户**而非 TTL 上下文回填字段。

## 错误码（FOREIGN_CODES 纪律：本模块零新增）

- 租户面用契约码：`TENANT-0404`（未找到，404）、`TENANT-0409`（编码重复，409）、`TENANT-0423`（已停用，
  预留给 identity 登录侧拦截）。
- 套餐面无专属码段：未命中用平台 `FORGE-0404`；编码重复用平台冲突码 `FORGE-0423` + 具体消息"套餐编码已存在"、
  HTTP 409（若后续需要专属码，须先在 contract 模块加码并同步四契约 FOREIGN_CODES 冻结集）。

## 接口（实际路径含 /admin-api 前缀）

- `POST /tenants`、`GET /tenants/{id}`、`GET /tenants?code=`、`PUT /tenants/{id}`、
  `POST /tenants/{id}/disable`、`POST /tenants/{id}/enable` → `Result<TenantDTO>`
- `POST /tenant-packages`、`GET /tenant-packages/{id}`、`GET /tenant-packages?code=`、
  `PUT /tenant-packages/{id}`、`DELETE /tenant-packages/{id}`（软删）→ `Result<TenantPackageDTO>`
- 契约查询 bean：`TenantQueryService`（findById/findByCode → `Optional<TenantDTO>`，跨模块同步消费）

## 集成实证

`TenantServiceIntegrationTest`（继承 `AbstractIntegrationTest`，真实 postgres:18-alpine 容器；
`@SpringBootTest(NONE)` + `@Import(TenantServiceAutoConfiguration)`）：create→findByCode/Id（服务+契约双路）、
重复编码 TENANT-0409/409、disable→状态+TenantDisabled 事件、幂等停用/重新启用、TENANT-0404、
**全局表实证**（平台视角写入不被写守护拒绝、任意租户视角可见同一行、审计 createdBy=system）、
套餐 CRUD/软删/编码复用——6/6 全绿。控制器薄层（校验+Result 包装），集成测试聚焦 service；
RANDOM_PORT 全链路（含 /admin-api 前缀）由 demo 装配（Task 8）承载。
