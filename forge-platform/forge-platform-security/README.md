# forge-platform-security

当前用户/租户上下文抽象：`CurrentUser`/`CurrentTenant` 读取接口 + `CurrentUserProvider`/
`CurrentTenantProvider` SPI，附 ThreadLocal 实现（跨线程隔离由使用方保证，测试已守护）。

- 无认证实现：Phase 2 由 Spring Security 认证链路填充 Provider
- 依赖：platform-core
- 状态：⚙️ 半成品（有意，仅上下文抽象）；排期：Phase 2
