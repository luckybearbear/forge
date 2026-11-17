# forge-platform-security

认证与租户上下文：`TenantContextHolder`（TTL 承载，TokenAuthFilter 填充/链尾清理）、
`CurrentUser`/`CurrentUserProvider` SPI + `ThreadLocalUserProvider`（filter 在用，Web 场景
须 finally clear）、`TokenAuthFilter`/`ForgeAuthentication`、`TokenStore` SPI、`@TenantIgnore`
（AOP 切面由 forge-security-starter 装配）。

> 终审清理（Phase 2a）：删除无消费方的 `CurrentTenant`/`CurrentTenantProvider`/
> `ThreadLocalTenantProvider`——租户上下文统一走 `TenantContextHolder`（+ platform-core 的
> `CurrentTenantSupplier` 解耦接口），不再保留第二套租户快照抽象。

- 依赖：platform-core
- 状态：Phase 2a（上下文 + 认证 filter 就绪；Redis/内存 TokenStore 装配在 forge-security-starter）
