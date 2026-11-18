# forge-security-starter

安全层 starter：引入即得无状态认证（Bearer token）+ 租户上下文填充 + `@TenantIgnore` 平台视角 AOP +
**方法安全（@EnableMethodSecurity）** + 401/403 统一 `Result` 输出 + **改密会话失效监听** +
**停用租户即时全下线监听**。
token 存储默认 Redis（随件携带 data-redis 栈），无 Redis 时自动降级内存实现（启动 warn）。

## 自动装配（`ForgeSecurityAutoConfiguration` + `ForgeTokenStoreFallbackAutoConfiguration`）

- `SecurityFilterChain`（`@ConditionalOnMissingBean`，应用可整体替换）：无状态 + CSRF off +
  permit-all（`forge.security.permit-all`，默认登录/健康/文档路径）+ **`/admin-api/**` 需
  `hasAuthority("PLATFORM")`**（Task 6 评审 Important-2：MEMBER token 403 FORGE-0403；登录例外
  `/admin-api/auth/login` 在 permitAll 先匹配不受影响；identity adminLogin grant 携带 PLATFORM
  authority）+ 其余 authenticated；401/403 → `ForgeSecurityResponses`（`FORGE-0401/0403`）
- `TokenAuthFilter` 以 `new` 经 `addFilterBefore` 注入链（**不暴露为 @Bean Filter**，防 Boot 全局注册二次执行）：
  认证 + 租户/用户上下文填充 + 滑动续期（TTL = `forge.security.token-ttl`），链尾 finally 清理
- `TokenStore`：Redis 在类路径（`RedisOperations`）且未自定义 → `RedisTokenStore`；否则由
  `ForgeTokenStoreFallbackAutoConfiguration`（`after` 定序）降级 `InMemoryTokenStore` + 启动 warn
- `@TenantIgnore` 切面（starter-aspectj）：方法/类级 `@Around`，进入置 `ignore=true`、finally 还原
- **`@EnableMethodSecurity`（Task 4 裁定归属本 starter）**：激活 `@PreAuthorize`（identity 的
  `user:manage`、authorization 的 `role:manage` 门禁等）——方法安全是安全能力而非服务挂靠；
  重复声明幂等。Phase 2b Task 3 曾挂 IdentityServiceAutoConfiguration，已移除
- **`UserPasswordChangedListener`（Task 4 安全输入 2）**：消费 identity 契约事件
  `UserPasswordChanged` → `TokenStore.revokeByUserId`（同步——改密返回时旧 token 已失效）。
  吊销按 userId 全域，无租户语义（不 set 租户）；发布方事务回滚时吊销已生效（安全方向偏差，
  用户重登即可，无危害）
- **`TenantDisabledListener`（Phase 3 前置 Task 1）**：消费 tenant 契约事件
  `TenantDisabled` → `TokenStore.revokeByTenantId`（同步——停用返回时该租户存量 token 已全下线）。
  吊销按 tenantId 全域，无租户语义（不 set 租户）；tenantId=null 的 admin token 不入租户索引、
  不受影响；发布方事务回滚时吊销已生效（安全方向偏差，重新启用后重登即可，无危害）

## 属性

| 键 | 默认 | 说明 |
|---|---|---|
| `forge.security.token-ttl` | `8h` | 签发 TTL + 滑动续期窗口 |
| `forge.security.permit-all` | `/app-api/auth/login,/admin-api/auth/login,/actuator/health,/v3/api-docs/**,/swagger-ui/**,/swagger-ui/index.html` | 免认证路径（Ant 风格） |

## RedisTokenStore 存储结构

- 键 `auth:token:{token}`（opaque UUIDv7）→ String 值 `StoredToken` JSON（token/grant/expiresAt），
  键级 TTL 即生命周期；`refresh` 重写值 + 重设 TTL（条件写 Lua，防吊销复活）+ 同步续索引 TTL；
  `resolve` 对 JSON 内 `expiresAt` 二次校验（防 refresh 与逐出竞态）。选 String+JSON 而非 hash：
  Redis hash 无单键整体 TTL 的便捷 API。
- **user 索引（Task 4 安全输入 2）**：`auth:user-tokens:{userId}` = SADD token + 键级 TTL
  （= token TTL，多 token 取较长者）；`revokeByUserId` 遍历索引删 token 键 + 清索引
  （只影响目标用户，幂等）。`InMemoryTokenStore` 同语义（反向 Map）。
- **tenant 索引（Phase 3 前置 Task 1，双索引）**：`auth:tenant-tokens:{tenantId}` = SADD token +
  键级 TTL；tenantId=null 的 admin token **不入本索引**（平台管理员跨租户，不受租户吊销影响）；
  `refresh` 续期同步续两索引；`revokeByTenantId` 遍历索引删 token 键 + 清索引（只影响目标租户，
  幂等）。`revoke(token)`/`revokeByUserId` 不清 tenant 索引成员（成本）——残留随
  `revokeByTenantId` 遍历幂等 DEL 回收，语义同 user 索引惰性残留。`InMemoryTokenStore` 同语义（反向 Map）。


## Boot 4 注意事项（实证）

- AOP starter 名为 **`spring-boot-starter-aspectj`**（4.1.2 BOM 无 `starter-aop`）
- 本配置 `before` Boot 4 的 `ServletWebSecurityAutoConfiguration`
  （`org.springframework.boot.security.autoconfigure.web.servlet`，Boot 3 的 `SpringBootWebSecurityConfiguration` 已更名拆分）：
  双方均 `@ConditionalOnMissingBean` 语义，不显式定序仅靠类名字典序不可靠
- `TokenAuthFilter` 锚 `ExceptionTranslationFilter`（非 `AuthorizationFilter`）：锚 AuthorizationFilter 时
  filter 的 finally 清理先于 `ExceptionTranslationFilter` 判定，匿名请求恒 403 而非 401
- TokenStore 兜底与 Redis 分支**不能**同置一个自动配置类的内嵌类（内嵌类加载顺序不受 `@Order`/声明序约束），
  拆独立自动配置类以 `after` 定序

## 集成实证

`SecurityStarterIntegrationTest`（继承 platform-test 的 `AbstractIntegrationTest`，真实 redis:8-alpine 容器
`@ServiceConnection` 直连）：未认证 401 `FORGE-0401` / 有效 token 200 + 租户填充（断言 `RedisTokenStore` 类型防兜底静默）/
revoke 后 401 / `@TenantIgnore` 平台视角——全绿（RED 复现：imports 注册行注释后 4/4 context 失败）。

`adminApiRequiresPlatformAuthority`（评审 Important-2）：MEMBER token 访问 /admin-api 探针 403
`FORGE-0403` / PLATFORM token 200 / 登录例外路径不落 403。

`TokenRevocationIntegrationTest`（Task 4 安全输入 2，同容器）：`revokeByUserId` 只吊销目标用户 +
索引落盘/清理 + 幂等；`UserPasswordChanged` 事件 → 旧 token 即时不可 resolve（RED：监听器未注册时
事件发布后 token 仍可 resolve）；identity 侧联测见
`forge-service-identity` 的 `changePasswordRevokesExistingTokensImmediately`（登录两 token → 改密 → 双双失效，新密码可登）。

`TenantDisabledRevocationIntegrationTest`（Phase 3 前置 Task 1，同容器）：两租户各两 token +
admin token（tenantId=null）→ `revokeByTenantId`/`TenantDisabled` 事件 → 目标租户全部 resolve 空、
邻居租户与 admin token 不受影响；tenant 索引落盘（SADD + TTL）/清理 + 幂等；`refresh` 同步续
tenant 索引 TTL。
