# Forge Java 应用开发底座 — Phase 2（企业基础能力）设计规格

| 项 | 值 |
|---|---|
| 日期 | 2026-11-09 |
| 状态 | 已确认（会话内设计评审通过，四项分叉按推荐方案定案） |
| 决策人 | luckybearbear |
| 阶段 | Phase 2 / 共 5 阶段（分 2a 基建、2b 业务两批实施） |
| 前置 | Phase 1 已交付（`docs/superpowers/specs/2026-11-09-forge-foundation-phase1-design.md`，f25773f） |

---

## 1. 背景与目标

Phase 1 交付了可独立运行/测试/发布的工程底座（19 模块、43 测试、零 DB）。Phase 2 在其上建设 **SaaS 的企业基础能力**：多租户、身份认证、组织架构、角色权限、数据审计，并把数据层从"有意半成品"升级为实装（PostgreSQL + Flyway + Testcontainers）。

Phase 2 完成后，底座应能支撑第一个真实 SaaS 产品（模具生命周期管理）直接开发业务模块。

## 2. 范围

### 2.1 交付（分两批）

**2a 基建批**（先出可用中间产物）：

1. 数据层实装：PostgreSQL + Flyway + JPA（租户 `@TenantId` 装配）+ platform-data 重写为 JPA 基线
2. `forge-data-starter` / `forge-security-starter` 拆分
3. 租户上下文（TTL）+ 四入口隔离骨架（Web filter / DB / Redis 前缀 / 事件显式传播）
4. Spring Security 骨架：TokenAuthFilter + TokenStore（Redis 优先，内存降级）+ SecurityFilterChain 默认（starter 自测验证，业务端点 2b）
5. platform-cache 落地（spring-data-redis + 租户前缀 CacheManager）
6. platform-web 增强：body 重复读、admin/app 双端前缀路由、Long→String
7. Modulith 事件 registry JDBC 化，`@ApplicationModuleListener` 从 Phase 1 降级中恢复
8. platform-test：Testcontainers 基类（PostgreSQL + Redis，`@ServiceConnection`）
9. demo-app 连库升级（内存→JPA 租户表）作为基建验证载体
10. 数据审计基线：JPA Auditing 桥接 + 访问/错误日志事件落库

**2b 业务批**（按依赖序 tenant → identity → organization → authorization）：

1. `service-tenant`：租户生命周期、套餐、启停
2. `service-identity`：租户用户 + 平台管理员、登录/登出/改密
3. `service-organization`：组织树、部门、岗位、用户挂岗
4. `service-authorization`：角色、权限点、分配；数据权限**模型预留**
5. 四个服务的契约模块（接口/DTO/事件/错误码）
6. demo-app 接入认证与权限（受保护端点、hasAuthority 用例、双租户全链路演示）

### 2.2 出界清单（Phase 2 不做）

- 数据权限的 SQL 级过滤实施（只落 Role.data_scope 模型 + 查询接口预留，Phase 3）
- 幂等/限流/分布式锁（yudao protection 模式，Phase 3）
- OIDC/SSO、短信/扫码登录（登录服务留认证策略扩展位，Phase 3+）
- 消息中心/通知、菜单动态路由后端
- 生产结构化日志（Phase 4）、Spotless（Phase 4）
- ScopedValue 替换 TTL 的复评（Phase 3+）
- 微服务/Spring Cloud（未排期）

## 3. 决策表（四项分叉定案）

| 决策 | 定案 | 理由与代价 |
|---|---|---|
| 认证 | **不透明 Token + Redis** | 撤销/续期/强制下线容易；微服务时代可平滑演进网关验签；代价：依赖 Redis（有内存降级） |
| 租户隔离 | **JPA + Hibernate `@TenantId`（共享库共享表 + tenant_id 列自动过滤）** | 应用层自动改写防手滑泄漏；**代价：services 持久化从 Spring Data JDBC 切 JPA——Phase 1 零实体，platform-data 本就是有意半成品，重写面 ≈ 1 个基类 + README，切换成本最低的时机就是现在**。备选（PG RLS / SD JDBC 显式谓词 / schema-per-tenant）已在会话内评审后放弃 |
| 分期 | **一份 spec、两批实施（2a/2b）** | 一次设计定形；2a 结束即有可用产物（连库 demo + 隔离证明） |
| 横切项 | **四项全进**（Long→String、body 重复读、双端前缀、日志落库） | 幂等/限流/锁推迟 Phase 3 |

### 3.1 对 Phase 1 spec 的联动修订

- Phase 1 spec §6.4（platform-data 半成品描述）自本 spec 生效后被 JPA 基线取代；SD-JDBC 审计基类删除，`forge-platform-data` 依赖改为 `spring-data-jpa`。
- Phase 1 spec §10"Modulith registry 持久化留待 Phase 2"于 2a 兑现（`spring-modulith-starter-jdbc` + `event_publication` 表）。
- Demo REST 前缀从 `/api/**` 迁移为 `/app-api/**`（双端路由引入，demo 尚无外部消费者，迁移无成本）。

## 4. 工程结构增量

```
forge-services/
├── forge-service-tenant/           # 2b：租户、套餐（表：tenant, tenant_package）
├── forge-service-identity/         # 2b：identity_user（租户表）, platform_admin；认证策略位
├── forge-service-organization/     # 2b：org_unit, position, user_position
└── forge-service-authorization/    # 2b：auth_role, auth_permission, auth_role_permission, auth_user_role

forge-contracts/
├── forge-contract-tenant/          # TenantDTO / TenantQueryService / 错误码 TENANT-
├── forge-contract-identity/        # UserDTO / UserQueryService / UserCreated 事件 / IDENT-
├── forge-contract-organization/    # OrgUnitDTO / OrgTreeQueryService / ORG-
└── forge-contract-authorization/   # UserPermissionsQuery / AUTH-

forge-platform/
├── forge-platform-data/      # 重写：JPA 基线（§6.3）
├── forge-platform-security/  # 升级：Token 机制 + Security 集成 + TTL 租户上下文（§6.1/6.2）
├── forge-platform-cache/     # 落地：RedisCacheFacade + TenantRedisCacheManager
├── forge-platform-web/       # 增强：body 重复读 / 双端前缀 / Long→String
└── forge-platform-test/      # 增强：AbstractIntegrationTest（Testcontainers）

forge-starters/
├── forge-data-starter/       # 新：JPA+Flyway+PG 驱动+租户 Hibernate 装配+Modulith JDBC registry
└── forge-security-starter/   # 新：Security 装配+TokenStore（Redis 优先/内存降级）+filter 注册

forge-distributions/forge-demo-app/
└── 内存 → 连库（mold/maintenance 迁移为 JPA 租户实体）+ 登录保护 + 双租户演示
```

每个服务模块自带 `src/main/resources/db/migration`（Flyway 扫描 classpath 多 jar 合并执行，`V{yyyyMMddHHmm}__` 时间戳全局定序，域前缀表名）——沿用 Phase 1 数据库规范。模块目录与 artifactId 均带 `forge-` 前缀（与 Phase 1 `forge-contract-mold` 同约定），全部入 forge-bom 管理。

## 5. 依赖规则增量

新模块遵守 Phase 1 依赖铁律，另增：

| 调用方 | 允许 | 禁止 |
|---|---|---|
| service-tenant/identity/organization/authorization | platform、**相互间只走 contracts**（authorization 依赖 identity/tenant 契约合法） | 直接依赖其他 service 内部实现 |
| contracts（新增四个） | platform-core | services 实现 |
| forge-data-starter / forge-security-starter | platform 对应模块 | 业务模块、其他 starter 循环 |

服务间典型协作（约束为打样模式）：登录时 identity 调 authorization 契约 `UserPermissionsQuery` 取权限点；identity 调 tenant 契约校验租户状态；organization 变更发领域事件（`OrgUnitChanged`）供审计消费。

## 6. 关键技术设计

### 6.1 多租户：上下文与四入口

**上下文承载**：`TenantContextHolder`（platform-security）基于 **TransmittableThreadLocal**（`com.alibaba:transmittable-thread-local`，BOM 管理版本，yudao 同款）：`tenantId` + `ignore` 标志 + `getRequiredTenantId()`。选 TTL 而非 JDK 25 ScopedValue 的理由：`@Async`/线程池传播 TTL 开箱即用，ScopedValue 的 executor 绑定语义尚不适配（Phase 3+ 复评，出界清单已记）。

四入口统一隔离：

1. **Web**：`TokenAuthFilter`（`FilterOrder.SECURITY_FILTER=-100`）认证同时填充租户上下文并在 finally 清理（修订：原 TenantContextFilter(-120) 先于认证运行却依赖认证结果，存在顺序矛盾，职责并入认证过滤器）；`FilterOrder.TENANT_FILTER=-120` 段保留给未来 header 直传租户场景。`app-api/**` 请求无有效租户 → 401/400。
2. **DB**：`CurrentTenantIdentifierResolver`（实现于 platform-data，读 TenantContextHolder）注册为 `HibernatePropertiesCustomizer`；`@TenantId` 实体自动过滤。**未标注 `@TenantId` 的实体 = 全局表**（天然表级逃生口，等价 yudao ignore-tables）。
3. **Redis**：`TenantRedisCacheManager`（@Primary，事务感知可选）为 cache key 拼 `t:{tenantId}:` 前缀；`CacheFacade` 同规则。
4. **事件**：**不依赖隐式上下文传播**——跨模块事件 payload 必须携带 `tenantId` 字段，监听器入口显式 `set`/`finally clear`。理由：可调试、不依赖 TTL agent/executor 包装（Modulith 异步监听用非池化线程时 TTL 不保证）。

**@TenantIgnore 语义（与 yudao 的差异，明确记录）**：Phase 2 的 ignore = **平台视角**（resolver 返回 `"0"`，租户表中不存在 `tenant_id='0'` 的行 → 查询恒空集而非全量）。跨租户全量查询（报表类）不提供注解捷径，须显式 native/JPQL 且过代码评审。注解形式（方法级 AOP 置 ignore 标志）+ `forge.security.tenant.ignore-urls` 配置（登录、租户注册等）。

### 6.2 认证与 Spring Security

- **Token**：UUIDv7 不透明串；Redis hash `auth:token:{token}` → `{userId, tenantId, userType, authorities}`，TTL 8h **滑动续期**（filter 命中即续）；登出 = 删除（即时失效）。
- **TokenStore SPI**（platform-security）：`create/resolve/revoke`；`forge-security-starter` 提供 `RedisTokenStore`（依赖 spring-data-redis），无 Redis 配置时降级 `InMemoryTokenStore` + 启动 warn（仅限开发）。
- **TokenAuthFilter**（`FilterOrder.SECURITY_FILTER=-100`，`OncePerRequestFilter`）：Bearer 解析 → TokenStore → 构造 `ForgeAuthentication`（userId/tenantId/authorities）→ SecurityContext + CurrentUser + TenantContext 填充；finally 全清理。
- **SecurityFilterChain**（starter 默认，`@ConditionalOnMissingBean` 可覆盖）：无状态、CSRF off、permitAll = 登录端点 + actuator health + swagger（dev profile）；其余 authenticated；401/403 由入口点/拒绝处理器输出统一 `Result`（`FORGE-0401/0403`，Phase 1 预留码启用）。
- **密码**：`DelegatingPasswordEncoder`（BCrypt 默认），`PasswordHasher` 工具（platform-security）。
- **双端登录**：`POST /app-api/auth/login`（租户 + 用户名/密码 → identity_user）与 `POST /admin-api/auth/login`（平台管理员 → platform_admin，独立表、非租户表）。
- **方法级权限**：`@PreAuthorize("hasAuthority('mold:create')")`；authorities 来自登录时 authorization 契约查询（缓存 Redis `auth:perm:{tenantId}:{userId}`，角色变更时失效）。

### 6.3 数据层 JPA 基线（platform-data 重写）

- 依赖：`spring-data-jpa`（Boot BOM 管 Hibernate 7 版本）；实体规范：
  - `AbstractAuditableEntity`（`@MappedSuperclass`）：`String id`（`@PrePersist` 内空则由内置静态 `UuidV7Generator` 赋值）、`Long version`（`@Version`）、审计四字段（`@CreatedBy/@CreatedDate/@LastModifiedBy/@LastModifiedDate` + `AuditingEntityListener`）、软删除 `Boolean deleted`（`@SQLDelete` UPDATE 置位 + `@SQLRestriction("deleted = false")`）。
  - `AbstractTenantEntity extends AbstractAuditableEntity`：`@TenantId String tenantId`（`nullable=false`）。
- 审计桥接：`forge-data-starter` 装配 `AuditorAware`（读 platform-security CurrentUser）+ `@EnableJpaAuditing`。
- 软删除与 `@TenantId`/`@SQLRestriction` 叠加行为以集成测试实证（风险 §12）。
- Flyway：沿用 Phase 1 规范（各模块 `db/migration`、时间戳命名、一次性变更、域前缀表名、禁跨域外键）。
- 乐观锁冲突映射：`ObjectOptimisticLockingFailureException` → **HTTP 409 + `FORGE-0423`**（platform-web 异常处理器新增分支）。

### 6.4 platform-cache 落地

- `RedisCacheFacade implements CacheFacade`（Phase 1 SPI 兑现）：put/get/evict，JSON 序列化，key 规则 `t:{tenantId}:{key}`（ignore 时无前缀）。
- `TenantRedisCacheManager`：`@Primary CacheManager`，供 `@Cacheable` 使用，同前缀规则。

### 6.5 platform-web 增强

- **Body 重复读**：`CacheRequestBodyFilter`（`FilterOrder.REQUEST_BODY_WRAPPER=-150`）包装 `ContentCachingRequestWrapper`；默认开启，`forge.web.access-log.max-body-size=8KB` 截断，`exclude-urls`（上传类）。
- **双端前缀路由**：`WebMvcRegistrations` 自定义 `RequestMappingHandlerMapping`——包名 `**.controller.admin.**` → `/admin-api`、`**.controller.app.**` → `/app-api`（`forge.web.api-prefix.enabled` 默认 true）。demo 的 controller 迁入 `controller.app` 包。
- **Long→String**：全局 Jackson `SimpleModule`（Long/long → String 序列化）在 starter 装配；`Instant` ISO 输出不变。

### 6.6 访问/错误日志落库（Modulith 事件兑现）

- `ApiAccessLogFilter`（`FilterOrder.ACCESS_LOG_FILTER=-80`）：method/path/userId/tenantId/duration/resultCode/body 摘要 → 发布 `AccessLogRecorded`（契约事件，含 tenantId）→ platform-audit 的监听器 `@ApplicationModuleListener` 异步消费落 `api_access_log` 表（全局表，冗余 tenant_id 列便于检索）。
- `GlobalExceptionHandler` 兜底分支发布 `ErrorLogRecorded`（Phase 1 预留点兑现，落 `api_error_log`）。
- 监听器按 §6.1-4 显式恢复租户上下文；事件经 JDBC registry 持久化（完成/未完成可追溯）。

### 6.7 Starter 拆分

| Starter | 内容 | 不含 |
|---|---|---|
| `forge-spring-boot-starter`（既有，不动） | web 基线（异常/响应/OpenAPI/actuator/logback） | DB、安全 |
| `forge-data-starter` | data-jpa + PG 驱动(runtime) + Flyway + platform-data + 租户 Hibernate 装配（resolver/AuditorAware/@EnableJpaAuditing）+ `IdGenerator` Bean 上移（`@ConditionalOnMissingBean`，终审遗留项兑现）+ `spring-modulith-starter-jdbc` | 安全 |
| `forge-security-starter` | spring-boot-starter-security + platform-security + TokenStore 装配（Redis 优先/内存降级）+ filter 注册（TENANT/SECURITY order）+ 默认 SecurityFilterChain | DB（token 需要缓存时经 platform-cache 接口） |

demo-app 依赖三 starter（web + data + security）。

### 6.8 测试基建（platform-test）

- `AbstractIntegrationTest`：`@Testcontainers(disabledWithoutDocker = true)` + 静态共享 `PostgreSQLContainer("postgres:18-alpine")` + Redis 容器 + Boot `@ServiceConnection`；无 Docker 环境 skip（surefire 统计可见 skipped 计数），CI 必须提供 docker runner（验收 §10）。
- `TenantTestSupport.runAs(tenantId, Runnable)`：包装 set/finally clear，供双租户用例。

### 6.9 数据权限（模型预留）

`Role.dataScope`（枚举：ALL / TENANT / DEPARTMENT / DEPT_TREE / SELF / CUSTOM）+ `DataScopeQueryService` 契约（返回用户的有效 scope）；SQL 级过滤实施与 CUSTOM 规则 Phase 3。

## 7. 服务模块设计（2b）

| 服务 | 领域模型（表见附录 A） | 主要端点（双端前缀） | 事件（契约） |
|---|---|---|---|
| tenant | Tenant（code/name/package/status/expiredAt，全局表）、TenantPackage | admin：租户 CRUD/启停、套餐 CRUD；app：无 | TenantCreated/TenantDisabled |
| identity | IdentityUser（租户表：username/phone/passwordHash/status，唯一 (tenant_id,username)）、PlatformAdmin（全局表，seed 初始 admin） | app：/auth/login、/auth/logout、/me、改密；admin：/auth/login、用户 CRUD | UserCreated/UserPasswordChanged |
| organization | OrgUnit（树：parentId/type/name/sort，租户表）、Position、UserPosition | app：组织树 CRUD、岗位 CRUD、用户挂岗 | OrgUnitChanged |
| authorization | Role（含 dataScope，租户表）、Permission（全局字典：code/type MENU\|API）、RolePermission、UserRole | app：角色 CRUD、角色授权、用户分角；admin：权限点字典管理 | RolePermissionsChanged（触发权限缓存失效） |

登录编排（identity）：校验租户状态（tenant 契约）→ 密码校验 → 取权限集（authorization 契约）→ TokenStore.create。

## 8. demo-app 升级

- 2a：mold/maintenance 从内存注册表迁移为 JPA 租户实体（`AbstractTenantEntity`），Flyway 建表，双租户隔离集成测试；事件改回 `@ApplicationModuleListener`（registry JDBC 持久化）。
- 2b：端点迁入 `controller.app` 包（`/app-api/molds` 等）并加 `@PreAuthorize`；集成测试覆盖：登录 → 受保护 CRUD → B 租户不可见 → 登出 → 401 → 无权限用户 403。

## 9. 数据库与错误码

附录 A：表清单（15 张：tenant 域 2 + identity 2 + organization 3 + authorization 4 + audit 2 + demo 2，另 Modulith `event_publication` 框架表）。

附录 B：错误码段增量登记 `TENANT-` / `IDENT-` / `ORG-` / `AUTH-`（各域契约模块内常量类，`docs/standards/error-codes.md` 同步更新）；平台段复用（`FORGE-0401/0403` 启用、`FORGE-0423` 乐观锁）。

## 10. 构建与环境

- 本地测试需 **Docker/Podman 可用**（Testcontainers 拉取 postgres:18-alpine、redis:8-alpine；首次较慢，之后走本地缓存）；无 Docker 时容器测试 skip。
- `docker compose --profile infra up -d` 提供 PG+Redis（Phase 1 compose 复用，PGDATA 已修）。
- CI：`.gitlab-ci.yml` 的 verify 作业需 docker executor（dind 或 socket 绑定）；镜像不变，脚本追加 `DOCKER_HOST` 透传说明注释。
- 新增依赖版本管理：`transmittable-thread-local` 入 forge-bom；testcontainers 版本由 Boot BOM 管理（实施时确认）。

## 11. 测试策略与验收标准

**2a 验收（Done）**：
1. Docker 在场：`./mvnw verify` 全绿（含容器集成测试）；Docker 缺席：容器测试 skip、其余全绿、skipped 计数可见
2. **双租户隔离集成测试**：租户 A 写入 mold 数据 → 租户 B 查询不可见；ignore（平台视角）查询恒空
3. Modulith：`event_publication` 表存在，事件完成记录可查；`@ApplicationModuleListener` 恢复生效
4. 审计：createdBy/createdAt 自动填充（AuditorAware 桥接用例）；软删除后查询不含已删行
5. starter 拆分后旧路径不回归：仅引 `forge-spring-boot-starter` 的应用零 DB/Security 依赖（验证用例）
6. body filter/双端前缀/Long→String 各至少 1 个用例
7. TokenStore 生命周期（create/resolve/续期/revoke）单测 + 内存降级启动 warn 用例

**2b 验收（Done）**：
1. app/admin 双端登录→受保护端点→登出→401 全链路集成测试
2. `hasAuthority` 权限控制：有权限 200 / 无权限 403 用例；角色变更后权限缓存失效用例
3. 双租户全链路：B 租户用户不可见 A 租户 mold 与 identity_user
4. 四服务 REST + 契约模块 + 集成测试全绿；每模块 README 更新；错误码登记表更新
5. demo swagger 双端完整可体验（登录获取 token → 调用业务端点）
6. `./mvnw verify` 全绿（总数以实施为准）

## 12. 风险与实施时验证项

| 风险/不确定 | 应对 |
|---|---|
| `@TenantId` + `@SQLRestriction`（软删除）叠加与关联 fetch 行为（Hibernate 7） | 2a 集成测试最先实证（双租户 + 软删除组合用例）；异常再评估软删除实现方式 |
| `@TenantId` 无运行时 ignore 开关 | 已定义"平台视角"语义（§6.1）；不提供全量捷径，评审把关 |
| TTL 在 Modulith 异步监听的传播 | 设计为事件显式携带 + 监听器显式 set/clear，不依赖传播（§6.1-4） |
| Boot BOM 是否管理 testcontainers 版本 | 实施时 dependency:tree 确认；未管理则 forge-bom 补 |
| Hibernate 7 `CurrentTenantIdentifierResolver` 注册方式（Boot 自动探测 vs customizer） | 实施时以官方文档 + 实证定，customizer 为兜底 |
| Testcontainers 首次拉镜像耗时 / CI docker runner 依赖 | 验收标准已区分 Docker 在场/缺席；CI 脚本加注释 |
| Redis 缺席场景 | InMemoryTokenStore 降级 + warn（仅开发）；demo 默认 profile 带 infra compose |
| JPA 切换遗漏 SD-JDBC 残留引用 | reactor 全量编译即暴露；platform-data README 重写 |

## 13. 后续阶段备忘（非本次范围）

- **Phase 3**：数据权限 SQL 过滤实施、幂等/限流/分布式锁、消息通知、文件中心、简单审批流
- **Phase 4**：结构化日志、Spotless、JaCoCo、Modulith 文档化、CI/CD 完善与镜像发布
- **Phase 3+**：ScopedValue 复评、OIDC/SSO（第二个应用出现时）、跨租户报表查询方案

---

## 附录 A：表清单（Phase 2 新建）

| 表 | 域 | 租户表 | 关键列 |
|---|---|---|---|
| tenant | tenant | 否 | id, code, name, package_id, status, expired_at |
| tenant_package | tenant | 否 | id, code, name, max_users, remark |
| platform_admin | identity | 否 | id, username, password_hash, status |
| identity_user | identity | **是** | id, tenant_id, username, phone, password_hash, status（唯一 (tenant_id, username)） |
| org_unit | organization | **是** | id, tenant_id, parent_id, type, name, sort |
| position | organization | **是** | id, tenant_id, name, remark |
| user_position | organization | **是** | id, tenant_id, user_id, position_id |
| auth_role | authorization | **是** | id, tenant_id, code, name, data_scope |
| auth_permission | authorization | 否 | id, code, name, type(MENU/API) |
| auth_role_permission | authorization | **是** | id, tenant_id, role_id, permission_id |
| auth_user_role | authorization | **是** | id, tenant_id, user_id, role_id |
| api_access_log | platform-audit | 否（冗余 tenant_id 列） | id, tenant_id, user_id, method, path, result_code, duration_ms, body_digest, created_at |
| api_error_log | platform-audit | 否（冗余 tenant_id 列） | id, tenant_id, user_id, exception, message, stack_digest, created_at |
| mold | demo | **是** | id, tenant_id, name, specification, status, created_at…（审计基类字段） |
| maintenance_order | demo | **是** | id, tenant_id, mold_id, mold_name, description, status |
| event_publication | Modulith 框架 | 否 | 框架管理 |

命名规范：小写 snake_case、域前缀、审计基类列全表统一（id/version/created_by/created_at/updated_by/updated_at/deleted）。

## 附录 B：错误码段登记表增量

| 前缀 | 归属 | 定义位置 | 状态 |
|---|---|---|---|
| TENANT- | 租户域 | contract-tenant | 2b 启用 |
| IDENT- | 身份域 | contract-identity | 2b 启用 |
| ORG- | 组织域 | contract-organization | 2b 启用 |
| AUTH- | 授权域 | contract-authorization | 2b 启用 |

（FORGE- 平台段沿用 Phase 1；MOLD-/MAINT- 沿用。）
