# forge-security-starter

安全层 starter：引入即得无状态认证（Bearer token）+ 租户上下文填充 + `@TenantIgnore` 平台视角 AOP +
401/403 统一 `Result` 输出。token 存储默认 Redis（随件携带 data-redis 栈），无 Redis 时自动降级内存实现（启动 warn）。

## 自动装配（`ForgeSecurityAutoConfiguration` + `ForgeTokenStoreFallbackAutoConfiguration`）

- `SecurityFilterChain`（`@ConditionalOnMissingBean`，应用可整体替换）：无状态 + CSRF off +
  permit-all（`forge.security.permit-all`，默认登录/健康/文档路径）+ 其余 authenticated；
  401/403 → `ForgeSecurityResponses`（`FORGE-0401/0403`）
- `TokenAuthFilter` 以 `new` 经 `addFilterBefore` 注入链（**不暴露为 @Bean Filter**，防 Boot 全局注册二次执行）：
  认证 + 租户/用户上下文填充 + 滑动续期（TTL = `forge.security.token-ttl`），链尾 finally 清理
- `TokenStore`：Redis 在类路径（`RedisOperations`）且未自定义 → `RedisTokenStore`；否则由
  `ForgeTokenStoreFallbackAutoConfiguration`（`after` 定序）降级 `InMemoryTokenStore` + 启动 warn
- `@TenantIgnore` 切面（starter-aspectj）：方法/类级 `@Around`，进入置 `ignore=true`、finally 还原

## 属性

| 键 | 默认 | 说明 |
|---|---|---|
| `forge.security.token-ttl` | `8h` | 签发 TTL + 滑动续期窗口 |
| `forge.security.permit-all` | `/app-api/auth/login,/admin-api/auth/login,/actuator/health,/v3/api-docs/**,/swagger-ui/**,/swagger-ui/index.html` | 免认证路径（Ant 风格） |

## RedisTokenStore 存储结构

键 `auth:token:{token}`（opaque UUIDv7）→ String 值 `StoredToken` JSON（token/grant/expiresAt），键级 TTL 即生命周期；
`refresh` 重写值 + 重设 TTL；`resolve` 对 JSON 内 `expiresAt` 二次校验（防 refresh 与逐出竞态）。
选 String+JSON 而非 hash：Redis hash 无单键整体 TTL 的便捷 API。

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
