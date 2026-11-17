# Forge Phase 2b（企业基础能力·业务批）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付四业务服务（tenant/identity/organization/authorization）+ 四契约模块 + 访问/错误日志落库 + demo 认证全链路——完成后底座可支撑真实 SaaS 产品开发。

**Architecture:** 服务=自动配置库（各 jar 自带 imports+自包扫描+EntityScan；互不依赖、只走 contracts，Maven 强制边界；Modulith 根包维持 `io.github.luckybearbear.forge.demo`）。实施序：加固包 → 契约×4 → tenant → identity → organization → authorization(+cache-starter) → 审计日志落库 → demo 认证接入 → 文档 → 终验。

**Tech Stack:** 2a 全栈（Boot 4.1.2/Hibernate 7 @TenantId/@SoftDelete/Testcontainers/Redis Token）+ 本批新增 forge-cache-starter。

**Spec:** `docs/superpowers/specs/2026-11-09-forge-foundation-phase2-design.md`（§2.1-2b、§7 服务设计、附录 A/B；§11.1 修订记录）

## Global Constraints

- 版本/构建/提交约定同 2a（`${revision}`、无 Lombok、UTF-8、conventional+中文、单条提交/任务、JDK 25 路径文件 `.superpowers/sdd/<本计划工作区>/jdk25-path.txt` 由 controller 提供）
- **依赖铁律**：service 之间禁止 Maven 依赖（只依赖 platform+contracts）；contract 只依赖 platform-core；starter 互禁（新增 cache-starter 同规）；**服务 jar 自带 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`**
- 服务模块包：`io.github.luckybearbear.forge.service.<domain>`（内部 `controller.app/controller.admin/app/domain` 分层）；契约包 `io.github.luckybearbear.forge.contract.<domain>`
- 双端路由：`controller.admin.*`→`/admin-api`、`controller.app.*`→`/app-api`（starter 已自动装配）
- 表：附录 A 清单（snake_case、域前缀、审计基类列、租户表带 tenant_id）；**软删除×唯一约束用 partial unique index `WHERE deleted = false`**（终审 2b 前置输入）
- 平台视角写守护：`AbstractTenantEntity` 持久化时 ignore → fail-fast（Task 1 落地）
- 错误码：TENANT-/IDENT-/ORG-/AUTH- 四段随契约模块定义并登记 `docs/standards/error-codes.md`
- 测试：每服务容器集成测试（extends AbstractIntegrationTest）；demo 全链路含登录/401/403/租户隔离
- **计划风格声明**：关键/新颖逻辑给全码；常规 CRUD 给精确的文件清单+签名+表结构+端点+断言（实施者按 Phase 1/2a 既有风格与 tenant 范式落地，评审对照签名与行为）

## 本计划新增架构决策（供 ledger 记录）

1. **服务=自动配置库**（上文 Architecture）——不扩大 Modulith 治理范围。
2. **forge-cache-starter 新增**（Task 6）：RedisCacheFacade/TenantRedisCacheManager 的装配点 + RedisTemplate JSON 序列化基线；权限缓存是其第一个消费者。
3. **demo 种子走 API**（Task 8）：`DemoDataSeeder`（ApplicationRunner）经服务端点/契约铺底（两租户/用户/角色/权限），不走跨域 SQL INSERT。
4. platform_admin 初始账号经 Flyway 固定 bcrypt hash seed（README 提示首登改密）。

---

### Task 1: 2b 前置加固包（终审遗留的基建项）

**Files:**
- Modify: `forge-platform/forge-platform-data/.../AbstractTenantEntity.java`（+写守护）
- Modify: `forge-starters/forge-security-starter/.../security/RedisTokenStore.java`（refresh Lua 原子化）
- Modify: `forge-starters/forge-spring-boot-starter/.../web/ForgeWebAutoConfiguration.java`（WebMvcRegistrations 覆盖治理）
- Modify: `forge-starters/forge-data-starter/.../data/ForgeDataAutoConfiguration.java`（customizer 让位）
- Modify: `forge-starters/forge-security-starter/.../security/ForgeSecurityAutoConfiguration.java`（SERVLET 守卫）
- Test: 各对应模块测试追加

**Interfaces:** Consumes 2a 全部；Produces: 平台视角写守护/Lua refresh/覆盖安全网

- [ ] **Step 1: AbstractTenantEntity 写守护**（fail-fast，静态 supplier 注入避免 data→security 依赖）：

```java
    /** 由 forge-data-starter 在装配时注入（core 的 CurrentTenantSupplier）；未注入时跳过检查（独立单测场景）。 */
    public interface TenantWriteGuard { boolean disallowPlatformWrites(); }

    private static volatile TenantWriteGuard writeGuard;

    public static void setWriteGuard(TenantWriteGuard guard) { writeGuard = guard; }

    @PrePersist
    @Override
    protected void assignId() {
        super.assignId();
        TenantWriteGuard guard = writeGuard;
        if (guard != null && guard.disallowPlatformWrites()) {
            throw new IllegalStateException(
                    "refusing to persist tenant entity under platform view (tenant_id would be '"
                            + ForgeTenantIdentifierResolver.PLATFORM_TENANT_ID + "'): " + getClass().getSimpleName());
        }
    }
```

（`AbstractAuditableEntity.assignId` 改为可覆写或把 @PrePersist 移到子类统一——以编译干净为准；data-starter 装配处 `AbstractTenantEntity.setWriteGuard(() -> supplier.ignore())`；集成测试：ignore 下 save 抛 IllegalStateException、租户上下文下正常。）

- [ ] **Step 2: RedisTokenStore.refresh Lua 原子化**（revoke 后不可复活）：

```java
    private static final org.springframework.data.redis.core.script.RedisScript<Boolean> REFRESH_SCRIPT =
            RedisScript.of("if redis.call('GET', KEYS[1]) then redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2]) return true else return false end", Boolean.class);
    // refresh: redisTemplate.execute(REFRESH_SCRIPT, List.of(key), value, String.valueOf(ttl.toMillis()))
```

测试：refresh 不存在的 key 不创建（EXISTS==0）；既有 key 续期。

- [ ] **Step 3: WebMvcRegistrations 覆盖治理**——ForgeWebAutoConfiguration 去掉 `@Import(ForgeApiPrefixConfiguration.class)`，改为：

```java
    @Bean
    @ConditionalOnMissingBean(WebMvcRegistrations.class)
    public WebMvcRegistrations forgeApiPrefixWebMvcRegistrations(ForgeApiPrefixProperties properties) {
        return () -> new ForgeApiPrefixRequestMappingHandlerMapping(properties);
    }
```

（ForgeApiPrefixConfiguration 保留类但不再 @Import 或删并入——取最小改动；新增覆盖测试：用户自带 WebMvcRegistrations bean 时 forge 的不注册且用户生效。）
- [ ] **Step 4: data-starter customizer 让位**——`forgeTenantHibernateCustomizer` 加 `@ConditionalOnBean(ForgeTenantIdentifierResolver.class)`（用户自带 CurrentTenantIdentifierResolver 时我们的 resolver bean 因 @ConditionalOnMissingBean 让位，customizer 随之不注册，避免属性键竞争）。
- [ ] **Step 5: SecurityChainConfiguration 加 `@ConditionalOnWebApplication(SERVLET)`**（非 Servlet 环境优雅退避）+ `@TenantIgnore`+`@Transactional` 组合集成用例（data-starter 测试：注解在事务方法上，内部查询为平台视角恒空——验证 @Order(0) 生效）。
- [ ] **Step 6: 全量 verify + Commit** `fix(platform): 2b 前置加固（平台视角写守护/Lua refresh/覆盖安全网/SERVLET 守卫）`

---

### Task 2: 四个契约模块（contracts-first）

**Files（每模块同构，共 4 个新 Maven 模块）：**
- Create: `forge-contracts/forge-contract-tenant|identity|organization|authorization/pom.xml`（parent forge-contracts，依赖仅 forge-platform-core + 测试栈）
- Create: 各模块 `src/main/java/io/github/luckybearbear/forge/contract/<domain>/` 下 DTO/接口/事件/错误码
- Modify: `forge-bom/pom.xml`（×4 入册）、`docs/standards/error-codes.md`（四段启用登记）

**Interfaces（精确签名，全部 Produces）：**

contract-tenant：
- `record TenantDTO(String id, String code, String name, String packageId, String status, Instant expiredAt)`
- `interface TenantQueryService { Optional<TenantDTO> findById(String tenantId); Optional<TenantDTO> findByCode(String code); }`
- `record TenantCreated(String tenantId, String code, String tenantName, Instant occurredAt)`、`record TenantDisabled(String tenantId, Instant occurredAt)`（均含租户字段约定——全局表事件无 tenantId）
- `enum TenantErrorCodes implements ErrorCode { TENANT_NOT_FOUND("TENANT-0404","租户不存在: {}"), TENANT_CODE_DUPLICATED("TENANT-0409","租户编码已存在: {}"), TENANT_DISABLED("TENANT-0423","租户已停用") }`

contract-identity：
- `record UserDTO(String id, String tenantId, String username, String phone, String status)`
- `interface UserQueryService { Optional<UserDTO> findById(String userId); }`（跨模块同步查询）
- `record UserCreated(String tenantId, String userId, String username, Instant occurredAt)`、`record UserPasswordChanged(String tenantId, String userId, Instant occurredAt)`
- `enum IdentityErrorCodes implements ErrorCode { IDENT_USER_NOT_FOUND("IDENT-0404","用户不存在: {}"), IDENT_USERNAME_DUPLICATED("IDENT-0409","用户名已存在: {}"), IDENT_BAD_CREDENTIALS("IDENT-0401","用户名或密码错误"), IDENT_PASSWORD_TOO_WEAK("IDENT-0400","密码强度不足") }`

contract-organization：
- `record OrgUnitDTO(String id, String parentId, String type, String name, int sort)`
- `interface OrgTreeQueryService { List<OrgUnitDTO> findSubTree(String rootOrgUnitId); }`
- `record OrgUnitChanged(String tenantId, String orgUnitId, String changeType, Instant occurredAt)`
- `enum OrganizationErrorCodes implements ErrorCode { ORG_UNIT_NOT_FOUND("ORG-0404","组织单元不存在: {}"), ORG_CYCLE_DETECTED("ORG-0409","组织树检测到环: {}"), ORG_POSITION_NOT_FOUND("ORG-0404","岗位不存在: {}") }`（注意：同码不同义不允许——枚举名唯一：`ORG_UNIT_NOT_FOUND("ORG-0404",...)`、`ORG_POSITION_NOT_FOUND("ORG-0414","岗位不存在: {}")`，实施时核对正则与唯一性）

contract-authorization：
- `interface UserPermissionsQuery { Set<String> authoritiesOf(String tenantId, String userId); }`
- `record RolePermissionsChanged(String tenantId, String roleId, Instant occurredAt)`
- `enum AuthorizationErrorCodes implements ErrorCode { AUTH_ROLE_NOT_FOUND("AUTH-0404","角色不存在: {}"), AUTH_PERMISSION_NOT_FOUND("AUTH-0414","权限点不存在: {}"), AUTH_ROLE_CODE_DUPLICATED("AUTH-0409","角色编码已存在: {}") }`

**Steps:**
- [ ] 建模块/类（签名逐字）、错误码正则+唯一性测试（四模块各 1 个测试类，断言 code 匹配 `^[A-Z][A-Z0-9]{1,9}-\d{4}$` 且 defaultMessage 含 `{}` 或完整文案）
- [ ] BOM×4、error-codes.md 登记（TENANT-/IDENT-/ORG-/AUTH- 启用+定义位置）
- [ ] `./mvnw -q clean -pl forge-contracts -am test` 全绿 → Commit `feat(contracts): 四域契约（DTO/查询接口/事件/错误码段）`

---

### Task 3: service-tenant（范式样板服务）

**Files:**
- Create: `forge-services/forge-service-tenant/`（pom：parent forge-services；依赖 forge-data-starter + forge-spring-boot-starter + forge-contract-tenant + forge-platform-core；test: starter-test + platform-test）
- Create: `src/main/java/io/github/luckybearbear/forge/service/tenant/`：
  - `TenantServiceAutoConfiguration.java`（@AutoConfiguration + @ComponentScan 本包 + @EntityScan/@EnableJpaRepositories 本包 domain）+ `META-INF/spring/...AutoConfiguration.imports`
  - `domain/TenantEntity.java`（**全局表** extends AbstractAuditableEntity——非租户表！code 唯一 partial index、name、packageId、status(INIT/ENABLED/DISABLED)、expiredAt）
  - `domain/TenantPackageEntity.java`（全局表：code/name/maxUsers/remark）
  - `domain/TenantRepository.java`、`TenantPackageRepository.java`（JpaRepository）
  - `app/TenantService.java`（create[编码唯一校验→TENANT-0409]/findById→DTO/findByCode/update/disable→发 TenantDisabled 事件+状态校验/enable；事务）
  - `app/TenantQueryServiceImpl.java`（实现 contract TenantQueryService，模块根包暴露 bean）
  - `controller/admin/TenantAdminController.java`（POST /admin-api/tenants、GET /{id}、GET ?code=、PUT /{id}、POST /{id}/disable、POST /{id}/enable；Result 包装）+ `controller/admin/TenantPackageAdminController.java`（/admin-api/tenant-packages CRUD）
  - `controller/admin/` 的请求 DTO（`CreateTenantRequest(@NotBlank code/@NotBlank name)` 等）
- Create: `src/main/resources/db/migration/V202611181000__create_tenant_tables.sql`：

```sql
CREATE TABLE tenant (
    id VARCHAR(36) PRIMARY KEY, code VARCHAR(64) NOT NULL, name VARCHAR(128) NOT NULL,
    package_id VARCHAR(36), status VARCHAR(16) NOT NULL, expired_at TIMESTAMPTZ,
    version BIGINT, created_by VARCHAR(36), created_at TIMESTAMPTZ, updated_by VARCHAR(36), updated_at TIMESTAMPTZ, deleted BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX uk_tenant_code ON tenant (code) WHERE deleted = FALSE;
CREATE TABLE tenant_package (
    id VARCHAR(36) PRIMARY KEY, code VARCHAR(64) NOT NULL, name VARCHAR(128) NOT NULL,
    max_users INT, remark VARCHAR(256),
    version BIGINT, created_by VARCHAR(36), created_at TIMESTAMPTZ, updated_by VARCHAR(36), updated_at TIMESTAMPTZ, deleted BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX uk_tenant_package_code ON tenant_package (code) WHERE deleted = FALSE;
```

- Test: `TenantServiceIntegrationTest extends AbstractIntegrationTest`（@SpringBootTest NONE + @Import(TenantServiceAutoConfiguration)：create→findByCode/Id、重复编码 409 码 TENANT-0409、disable→状态+TenantDisabled 事件（ApplicationListenerRecorder 或直接断言库）、启停后 enable、**写守护不适用于全局表**用例）
- Modify: `forge-services/pom.xml` 挂模块、forge-bom 入册

**Steps:** 测试先行（红：模块不存在）→ 实现 → 容器测试绿 → README → Commit `feat(service-tenant): 租户/套餐服务（范式样板）`

---

### Task 4: service-identity（双端登录）

**Files:**
- Create: `forge-services/forge-service-identity/`（依赖 + forge-contract-tenant + forge-contract-authorization；**不依赖任何 service**）
- Create: `.../service/identity/`：
  - `TenantIdentityServiceAutoConfiguration.java` + imports（同 Task 3 范式）
  - `domain/PlatformAdminEntity.java`（全局表：username 唯一 partial、passwordHash、status）
  - `domain/IdentityUserEntity.java`（**租户表** extends AbstractTenantEntity：username/phone/passwordHash/status；唯一 (tenant_id, username) partial）
  - 两 Repository
  - `app/AuthService.java`（**登录编排全码**）：

```java
    /** app 端登录：租户校验→密码→权限集→token。 */
    public LoginResponse appLogin(AppLoginRequest request) {
        TenantDTO tenant = tenantQueryService.findByCode(request.tenantCode())
                .orElseThrow(() -> BusinessException.of(TenantErrorCodes.TENANT_NOT_FOUND, request.tenantCode())
                        .withHttpStatus(404));
        if (!"ENABLED".equals(tenant.status())) {
            throw BusinessException.of(TenantErrorCodes.TENANT_DISABLED);
        }
        IdentityUserEntity user = userRepository.findByTenantIdAndUsername(
                        TenantContextHolder.getRequiredTenantIdForLogin(request.tenantCode()), request.username())
                .orElseThrow(() -> BusinessException.of(IdentityErrorCodes.IDENT_BAD_CREDENTIALS));
        // ↑ 上下文无租户（登录未认证）：repository 查询用显式 tenantId 参数（JPQL 忽略 @TenantId 注入？
        //   不——@TenantId 过滤总生效：登录查询需在 ignore=false 且上下文=目标租户下执行：
        //   TenantTestSupport 式 set/finally（service 内部显式 setTenantId(tenant.id()) → 查询 → finally clear）
        if (!passwordHasher.matches(request.password(), user.getPasswordHash())) {
            throw BusinessException.of(IdentityErrorCodes.IDENT_BAD_CREDENTIALS);
        }
        Set<String> authorities = permissionsQuery.authoritiesOf(tenant.id(), user.getId());
        StoredToken token = tokenStore.create(
                new TokenGrant(user.getId(), tenant.id(), "MEMBER", authorities), tokenTtl);
        return new LoginResponse(token.token(), token.expiresAt(), user.getId(), tenant.id());
    }

    /** admin 端登录：平台管理员（全局表，无租户）。 */
    public LoginResponse adminLogin(AdminLoginRequest request) { /* 同构：PlatformAdminEntity + TenantContextHolder.setIgnore(true) 查询场景 → grant.tenantId=null */ }
```

（实施说明：登录时上下文无租户，租户用户查询须 service 内显式 `TenantContextHolder.setTenantId(tenant.id())` + finally clear；admin 侧查全局表无需租户。`TenantContextHolder` 来自 platform-security（TTL），identity 依赖 platform-security 合法（platform 内部）。TokenGrant.tenantId=null 时 TokenAuthFilter 不填充租户——平台 token。）
  - `app/UserService.java`（本租户用户 CRUD：创建[唯一校验/初始密码/发 UserCreated]/改密[弱密码校验 IDENT-0400+发 UserPasswordChanged]/启停）
  - `controller/app/AuthAppController.java`（POST /app-api/auth/login、POST /app-api/auth/logout[当前 token revoke——token 从请求头取]、GET /app-api/auth/me[经 SecurityContext ForgeAuthentication 返回 userId/tenantId/authorities]）
  - `controller/admin/AuthAdminController.java`（POST /admin-api/auth/login、POST /admin-api/auth/logout、GET /admin-api/auth/me）
  - `controller/app/UserAppController.java`（/app-api/users CRUD，@PreAuthorize("hasAuthority('user:manage')")——权限点 seed 见 Task 6）
  - `app/IdentityUserQueryServiceImpl.java`（契约 UserQueryService）
- Create: `V202611181100__create_identity_tables.sql`（platform_admin + identity_user，含 partial unique + tenant 索引；platform_admin seed：`INSERT INTO platform_admin(id,username,password_hash,status,created_at,deleted) VALUES('...', 'admin', '<bcrypt 固定 hash>', 'ENABLED', now(), FALSE)`——hash 由 PasswordHasher 现场生成一次写入迁移脚本，README 记初始口令 admin123 与首登改密提示）
- Test: `IdentityServiceIntegrationTest`（app 登录成功/租户停用 TENANT-0423/坏密码 IDENT-0401 且不泄露存在性、admin 登录、logout 后 token 401、改密后旧密码失败、用户创建重复名 IDENT-0409、UserCreated 事件）
- Modify: 挂载+BOM

**Steps:** 同范式 → Commit `feat(service-identity): 双端登录/登出/改密 + 租户用户管理`

---

### Task 5: service-organization（组织树/岗位/挂岗）

**Files:** `forge-services/forge-service-organization/`（依赖 + forge-contract-identity）
- `domain/OrgUnitEntity.java`（租户表：parentId[根=null]/type(DEPT/GROUP)/name/sort）、`PositionEntity.java`（租户表）、`UserPositionEntity.java`（租户表：userId/positionId，唯一 (tenant_id,user_id,position_id) partial）
- `app/OrgUnitService.java`（create/update[**环检测 ORG-0409：沿 parentId 上溯含自身]**/move/delete[有子节点或挂岗拒绝]/subTree[递归]）、`PositionService`、`UserPositionService`（挂岗/摘岗，userId 存在性经 contract-identity UserQueryService 校验→IDENT-0404）
- `app/OrgTreeQueryServiceImpl.java`（契约）
- `controller/app/`：OrgUnitAppController（/app-api/org-units CRUD+GET /tree）、PositionAppController（/app-api/positions）、UserPositionAppController（/app-api/user-positions）
- `V202611181200__create_org_tables.sql`（org_unit/position/user_position + partial uniques + 索引）
- 事件：OrgUnitChanged（create/update/delete）
- Test：树 CRUD+环检测 409+删除保护+挂岗校验+双租户隔离（org 数据）+事件
- Commit `feat(service-organization): 组织树/岗位/用户挂岗`

---

### Task 6: service-authorization + forge-cache-starter

**Files A（cache-starter 先行，权限缓存的装配点）：**
- Create: `forge-starters/forge-cache-starter/`：`ForgeCacheAutoConfiguration`（RedisTemplate<String,Object> JSON 序列化基线 @ConditionalOnMissingBean、RedisCacheFacade bean、TenantRedisCacheManager @Primary @ConditionalOnMissingBean(CacheManager)）+ imports + README + BOM/挂载
- Test：容器集成（facade put/get/evict 带 `t:` 前缀——redis-cli 式断言经 RedisTemplate keys 扫描或 get 原始 key）

**Files B（authorization 服务）：**
- `forge-services/forge-service-authorization/`（依赖 + forge-contract-identity[UserCreated 监听建默认角色？否——默认角色由 TenantCreated 监听：租户创建→自动建"管理员"角色+授全权限点+绑定创建人？简化：TenantCreated 监听器创建默认 ADMIN 角色并授权全部权限点；demo seeder 显式绑定]）
- `domain/RoleEntity.java`（租户表：code/name/dataScope(枚举 ALL/TENANT/DEPARTMENT/DEPT_TREE/SELF/CUSTOM)）、`PermissionEntity.java`（全局表：code/name/type MENU|API）、`RolePermissionEntity.java`（租户表）、`UserRoleEntity.java`（租户表）
- `app/PermissionService.java`（字典 CRUD/全量）、`RoleService.java`（CRUD[code 唯一]/assignPermissions[发 RolePermissionsChanged→缓存失效]/assignToUser/dataScope 更新）
- `app/UserPermissionsQueryImpl.java`（契约实现 + **缓存**：`CacheFacade` key `auth:perm:{userId}` TTL 5min；RolePermissionsChanged 与 UserPasswordChanged 监听失效——事件经 Modulith registry，监听器显式 set 租户）
- `app/TenantCreatedListener.java`（@ApplicationModuleListener 消费 TenantCreated→默认 ADMIN 角色+全权限点）
- `controller/app/`：RoleAppController（/app-api/roles CRUD+PUT /{id}/permissions+PUT /{id}/data-scope）、UserRoleAppController（PUT /app-api/users/{userId}/roles）；`controller/admin/`：PermissionAdminController（/admin-api/permissions CRUD+GET）
- `V202611181300__create_auth_tables.sql`（四表+partial uniques+索引；**权限点字典 seed**：通用点 user:manage/role:manage/org:manage + demo 用 mold:create/mold:read/mold:repair/maint:read——INSERT 固定字典）
- Test：角色授权/缓存命中与失效（改角色权限后 5min 内即时失效）/TenantCreated 自动建角/数据权限字段持久化/双租户隔离
- Commit `feat(cache-starter,service-authorization): 缓存装配 + 角色/权限/分配/数据权限模型`

---

### Task 7: 访问/错误日志落库（spec §6.6 兑现）

**Files:**
- Create: `forge-platform/forge-platform-audit/`（升级占位：`AccessLogRecorded(String tenantId, String userId, String method, String path, int resultCode, long durationMs, String bodyDigest, Instant occurredAt)`、`ErrorLogRecorded(tenantId, userId, exception, message, stackDigest, occurredAt)`——放 platform-audit 的事件类型+监听器落库；audit 不依赖业务）
- Modify: `forge-platform/forge-platform-web/.../GlobalExceptionHandler.java`（兜底/业务分支发布 ErrorLogRecorded——经 ApplicationEventPublisher 可选注入，无发布者时跳过）
- Create: `forge-starters/forge-spring-boot-starter` 内 `ApiAccessLogFilter`（ACCESS_LOG_FILTER=-80：计时/取 CurrentUser+Tenant/body 摘要[经 ContentCachingRequestWrapper，≤max-body-size]/发布 AccessLogRecorded/finally）+ 装配（forge.web.access-log.enabled 开关已有属性）
- Create: audit 表迁移（platform-audit 自带 db/migration：api_access_log/api_error_log，附录 A 列，全局表冗余 tenant_id/user_id 列+索引；**audit 模块引入 forge-data-starter? platform 模块不能依赖 starter**——表迁移由 audit 模块自带但 audit 需要 JPA 落库实体？**决策**：audit 交付为"事件类型+JPA 实体+监听器"，但实体/监听器在**应用侧自动配置**（新 forge-audit 模块还是并入 data-starter？——最小方案：platform-audit 提供 `AuditAutoConfiguration`（含实体/监听器/imports），依赖 data-starter 的应用引 platform-audit 即得——platform-audit 依赖 forge-data-starter 违反"platform 不依赖 starter"! → 改：**forge-audit-starter**（第 4 个 starter，依赖 platform-audit+data-starter+modulith events）——计划修正：新增 forge-audit-starter，职责清晰且不破铁律）
- 修正后结构：platform-audit（事件类型+ApiAccessLogEntity/ApiErrorLogEntity+监听器类，依赖 platform-core/modulith events-api + spring-data-jpa for entity 注解）+ `forge-starters/forge-audit-starter/`（装配：filter 注册+imports+EntityScan）+ audit 表迁移放 audit-starter 的 db/migration
- Modify: BOM/挂载 ×2
- Test：请求→api_access_log 行（method/path/duration/resultCode/租户/用户）+ 异常请求→api_error_log 行 + 事件经 registry 异步完成
- Commit `feat(audit): 访问/错误日志落库（filter→Modulith 事件→JPA）`

---

### Task 8: demo 认证全链路接入

**Files:**
- Modify: `forge-distributions/forge-demo-app/pom.xml`（+forge-security-starter、四服务、forge-cache-starter、forge-audit-starter）
- Create: `.../demo/config/DemoDataSeeder.java`（ApplicationRunner @Profile("demo"|"test")：经服务 API/契约铺底——租户 t-mfg/t-mold 两家、各一管理员用户（mfg-admin/mold-admin，口令 Admin@123 bcrypt）、ADMIN 角色绑定全权限 + t-mold 的受限角色（仅 mold:read）；幂等[存在即跳过]）
- Modify: MoldController 等（类级或方法级 `@PreAuthorize("hasAuthority('mold:create')")` 等；Maintenance 同）
- Modify: application.yaml（+Profile demo 说明、security permit-all 默认已含登录端点）
- Test: `DemoAuthFlowIntegrationTest extends AbstractIntegrationTest`（@ActiveProfiles("test")）：
  1. 未带 token GET /app-api/molds → 401 FORGE-0401
  2. t-mfg 管理员登录 → token → POST /app-api/molds 200
  3. 同 token 查询 → 只见 t-mfg 数据；**t-mold 管理员登录同端点** → 不见 t-mfg 数据（双租户隔离经真实认证链）
  4. t-mold 受限角色用户 POST /app-api/molds → 403 FORGE-0403
  5. 角色权限变更（mold-admin 调 /app-api/roles）→ 受限用户权限缓存即时失效、获得 mold:create
  6. logout → 401
  7. 上述请求产生 api_access_log 行；制造异常 → api_error_log 行
  8. ModulithBoundaryTest 保持绿（demo 模块边界不受服务引入影响——服务在根包外）
- Modify: README（登录→swagger Authorize（Bearer）→业务端点体验流）
- Commit `feat(demo): 认证/权限/审计全链路接入 + 种子数据`

---

### Task 9: 文档收尾

- README（服务层四模块+audit/cache-starter 入表、认证快速开始）
- dependency-rules（服务=自动配置库形态、跨服务仅契约、事件清单表）
- getting-started（admin 初始口令与首登改密、demo profile/seeder）
- error-codes.md（四段+新码复核）
- spec §11.1 增补 2b 完成记录（如有偏差）
- Commit `docs: Phase 2b 文档与规范更新`（不 push）

---

### Task 10: 2b 终验与交付报告

- [ ] `./mvnw --batch-mode clean verify` 全绿（预计 110+ 测试）；记录模块/测试数
- [ ] 验收对照 spec §11-2b 六条逐项（登录全链路/hasAuthority/双租户全链路/四服务+契约+README/错误码登记/swagger 体验/verify 全绿）——**注意 §11-2b 与 §2.1-2b 逐项 1:1 对照**（终审流程教训），发现接缝漏项即报
- [ ] git push（controller 执行）+ 交付报告（提交列表/测试统计/验收结论/Phase 3 前置输入）

---

## Self-Review 记录

- **Spec 覆盖**：§2.1-2b 六项 ↔ Task 2(5)/3/4/5/6/8；§6.6 兑现↔Task 7；§6.9 数据权限模型↔Task 6；§7 服务表↔Task 3-6 端点/事件/表；附录 A 15 表↔Task 3-7 迁移；附录 B↔Task 2/9。终审 2b 前置输入：写守护↔T1、partial index↔T3-6、覆盖治理↔T1、平台写守护↔T1、refresh 原子化↔T1、@TenantIgnore+Tx↔T1、cache 装配↔T6、SERVLET↔T1。
- **占位符**：关键逻辑全码（写守护/Lua/登录编排/覆盖治理/装配范式）；CRUD 给精确签名/表/断言（风格声明于头部）。
- **类型一致性**：TokenGrant/StoredToken/TenantContextHolder/CurrentTenantSupplier 沿用 2a 签名；四契约接口在 T4/T5/T6 的实现方与消费方签名一致；事件类型跨任务一致（TenantCreated 双处引用同签名）。
