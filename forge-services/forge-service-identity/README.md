# forge-service-identity

身份服务：**双端登录/登出/改密 + 租户用户管理**（服务=自动配置库形态，照抄 `forge-service-tenant` 范式样板）。

## 模块形态（同 service-tenant 样板）

- **pom**：parent `forge-services`；依赖 `forge-data-starter` + `forge-spring-boot-starter` +
  **`forge-security-starter`**（TokenStore/PasswordHasher/TokenAuthFilter 装配，带 spring-security）+
  `forge-contract-tenant` + `forge-contract-identity` + `forge-contract-authorization` +
  `forge-platform-core`/`forge-platform-security`（主代码直接使用，显式声明）；
  **不依赖任何 service**（tenant/authorization 经契约消费）。test：`spring-boot-starter-test` + `forge-platform-test`。无 Lombok。
- **自动配置**：`IdentityServiceAutoConfiguration` = `@AutoConfiguration` + `@ComponentScan(模块根包)` +
  显式 `AutoConfigurationExcludeFilter` 自排 + `@EntityScan(domain)`（Boot 4 新包
  `org.springframework.boot.persistence.autoconfigure`）+ `@EnableJpaRepositories(domain)`；无 `@Bean`。
  `@EnableMethodSecurity` **已移至 forge-security-starter**（Task 4 裁定：方法安全是安全
  能力随 starter 装配；Phase 2b Task 3 曾挂本类）——`@PreAuthorize`（user:manage 门禁）由 starter 激活。
- **包布局**：`domain/`（实体+仓库）· `app/`（AuthService/UserService + 请求响应 record）·
  `controller/app/`、`controller/admin/`（双端前缀路由：包名决定 `/app-api`、`/admin-api` 前插）·
  模块根包（自动配置 + 契约实现 `IdentityUserQueryServiceImpl`）。
- **Flyway**：`V202611181100__create_identity_tables.sql` 随 jar 走——identity_user（租户表）、
  platform_admin（全局表）+ **种子管理员 admin/admin123**（固定 bcrypt hash；见下"初始口令"）。

## 表结构

- `identity_user`（**租户表**，extends AbstractTenantEntity）：`@TenantId` 判别式过滤/注入；
  (tenant_id, username) partial unique index（`WHERE deleted = FALSE`）——租户内唯一、跨租户可重名、
  软删行不占名。
- `platform_admin`（**全局表**，extends AbstractAuditableEntity）：username partial unique；无判别式
  ——admin 登录在无租户上下文下直查。
- **初始口令**：种子 `admin` / `admin123`（hash 由 `PasswordHasher` 现场生成一次写入迁移，带
  `{bcrypt}` 注册前缀）。**首登后应立即经改密通道更换**（当前 admin 侧暂无自助改密端点，可经 SQL
  重置或后续任务补齐——见已知取舍）；生产部署务必改掉种子口令。

## 登录上下文管理（@TenantId 判别式与租户上下文——核心语义）

- **app 登录**（`AuthService.appLogin`）：请求无认证无租户上下文。租户校验
  （契约 `TenantQueryService.findByCode` → TENANT-0404[404]；状态非 ENABLED → TENANT-0423）后，
  **显式 `TenantContextHolder.setTenantId(tenant.id())` → 查用户 → finally `clear()`**。
- **判别式在会话开启时解析**：租户用户查询经 `TransactionTemplate`（**REQUIRES_NEW** + readOnly）
  执行——新独立事务开新会话，保证判别式在 setTenantId 之后解析。若用 REQUIRED，消费应用开启
  OSIV（Boot 4 默认 `spring.jpa.open-in-view=true`，jar 实证 4.1.2 仍默认开）时请求级会话在
  setTenantId 之前已开启，判别式解析为平台租户 "0"（租户表恒空）→ 登录恒坏凭据。REQUIRES_NEW
  挂起请求级会话开新会话；无 OSIV（本模块测试 NONE 环境）时行为等价。
- **admin 登录**：platform_admin 全局表无 `@TenantId`——无租户上下文直查即可，不涉及判别式。
- **坏凭据不泄露存在性**：用户不存在/密码错误/账号停用 → 同一 `IDENT-0401` 同一文案
  （defaultMessage 无占位符），响应不可区分（防用户名枚举）；admin 侧同规则。
- **权限集**：`UserPermissionsQuery`（contract-authorization）经 **`ObjectProvider`** 注入——
  authorization 服务未装配时缺省空集 + **首次 warn 一次**（identity 独立可跑）；Task 6 装配后
  登录 grant 自动携带真实权限点。
- **token**：app 端 `TokenGrant(userId, tenantId, "MEMBER", 权限集)`；admin 端
  `TokenGrant(adminId, null, "PLATFORM", Set.of())`——`tenantId=null` 即平台 token
  （TokenAuthFilter 不填充租户上下文，2a 行为）。TTL 取 `forge.security.token-ttl`（与滑动续期同源，默认 8h）。
- **logout**：控制器自 `Authorization: Bearer` 提取当前 token → `tokenStore.revoke`；缺失/非 Bearer
  幂等无害。**/me**：读 SecurityContext 的 `ForgeAuthentication` → userId/tenantId/userType/authorities。

## 用户管理（UserService，租户视角）

- create：租户内 username 唯一（IDENT-0409/409）；初始密码弱校验（IDENT-0400）；发 `UserCreated`
  （payload 显式 tenantId——@TenantId 插入时才绑定，publish 时实体字段尚未回填，取当前上下文）。
- changePassword：旧密码错 IDENT-0401（与登录同码防枚举）；新密码弱 IDENT-0400；成功发
  `UserPasswordChanged`。**既有 token 由 forge-security-starter 的 UserPasswordChangedListener
  即时吊销**（Task 4 安全输入 2，随 Task 6 提交）：改密返回时旧 token 已不可 resolve（本服务
  仍不代持 token 索引——revokeByUserId 在 TokenStore 实现内）。
- enable/disable：幂等状态迁移；DISABLED 用户登录按坏凭据拒绝（不泄露停用状态）。
- findById：租户视角（判别式隔离，跨租户 id → IDENT-0404[404]）。
- **弱密码规则（IDENT-0400）**：≥8 位且同时含字母与数字——简单规则，复杂度策略属后续演进；
  对初始密码与改密新密码一致。
- **契约 `UserQueryService`**：租户视角查询（调用方须在租户上下文内；无上下文/平台视角恒空——
  平台视角租户表恒空，spec §6.1；不做跨租户解析）。

## 错误码（FOREIGN_CODES 纪律：本模块零新增）

- IDENT- 契约码：`IDENT-0401`（坏凭据）、`IDENT-0404`（用户不存在，404）、`IDENT-0409`
  （用户名重复，409）、`IDENT-0400`（密码强度不足）。
- 租户面沿用 tenant 契约码：`TENANT-0404`（404）、`TENANT-0423`（登录侧拦截停用租户）。

## 接口（实际路径含 /app-api、/admin-api 前缀）

- `POST /app-api/auth/login`（permit-all 默认项）、`POST /app-api/auth/logout`、`GET /app-api/auth/me`
- `POST /admin-api/auth/login`（permit-all 默认项）、`POST /admin-api/auth/logout`、`GET /admin-api/auth/me`
- `/app-api/users`：`POST`（创建）、`GET /{id}`、`PUT /{id}/password`、`POST /{id}/disable`、
  `POST /{id}/enable`——类级 `@PreAuthorize("hasAuthority('user:manage')")`（权限点分配见 Task 6）
- 契约查询 bean：`UserQueryService`（findById → `Optional<UserDTO>`，租户视角）

## 集成实证

`IdentityServiceIntegrationTest`（14 用例，真实 postgres:18-alpine + redis:8-alpine 容器；
`@SpringBootTest(NONE)` + `@Import(IdentityServiceAutoConfiguration)`，token 生命周期走
security-starter 的 **RedisTokenStore**）：app 登录成功（token 可 resolve、grant 含租户/MEMBER/
空权限集 + finally clear 卫生）、未知租户 TENANT-0404/404、停用租户 TENANT-0423、
**坏凭据三态同码同文案**（用户不存在/密码错/账号停用，不泄露）、admin 登录（tenantId=null +
PLATFORM + 空集；坏凭据同码）、logout 后 resolve 为空、改密（旧密码错 IDENT-0401/弱新密码
IDENT-0400/成功发 UserPasswordChanged 且旧密码失败新密码可登录）、**登录恒时**（未命中/密码错/停用/
成功各恰一次 bcrypt，可计数桩断言）、**改密即时吊销既有 token**（两 token 改密后双双失效，新密码
可登，identity × security-starter 联测）、用户创建重复名 IDENT-0409/409
+ UserCreated 单发 + 跨租户可重名、弱初始密码 IDENT-0400、契约查询租户视角隔离
（本租户命中/他租户空/平台视角空）。`IdentityServicePermissionsGrantTest`（1 用例）：UserPermissionsQuery
装配后登录 grant 携带真实权限点。

**测试包位于服务根包之外的独立分支**（`io.github...forge.identity.login|permissions`）+ 桩经 `@Bean`
注册：自动配置以服务根包做 @ComponentScan，若测试类栖身该包（tenant 单测试类范式可行），第二个
测试上下文会把主测试的 TestApp（本身即 @Configuration）当扫描发现一并注册，桩 bean 交叉泄漏/按名
冲突（BeanDefinitionOverrideException 实证）。**T5/T6 克隆本模块时：多测试上下文必须遵守此布局。**

控制器薄层（校验 + Result 包装），集成测试聚焦 service；RANDOM_PORT 全链路（HTTP + @PreAuthorize
403 + permit-all）由 demo 装配（Task 8）承载。

## 已知取舍（跨任务遗留输入登记 + 本模块）

- **TenantWriteGuard 静态注入无回收钩子**（Task 1 遗留）：无状态 supplier（读 TTL ignore 位）已
  安全——跨上下文无陈旧引用；不强制改造，知悉即可。
- **用户自带 resolver 时写守护消失**（Task 1 遗留）：data-starter 的 customizer
  （含 `AbstractTenantEntity.setWriteGuard` 装配）按 `@ConditionalOnBean(ForgeTenantIdentifierResolver)`
  让位——用户自管多租户接线时写守护随之不注入（fail-open）。平台默认装配下无影响。
- **admin 侧无自助改密端点**：种子口令更换暂经 SQL/后续任务；admin 密码哈希更新走
  `platform_admin.password_hash`（同 `{bcrypt}` 前缀格式）。
- **DISABLED 用户改密/登录语义**：登录按坏凭据拒绝；改密未拦截停用用户（管理员恢复流程的一部分）。
- **logout 未吊销该用户全部 token**：按 token 粒度吊销；全端下线走改密通道
  （UserPasswordChanged → security-starter 监听 revokeByUserId，已实现）。
