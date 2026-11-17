# forge-platform-audit

API 访问/错误审计（spec §6.6）：事件类型 + 全局表实体 + Modulith 异步监听器；装配出口在
`forge-starters/forge-audit-starter`（filter 注册/@EntityScan/表迁移）——本模块不依赖任何
starter。

- 事件：`AccessLogRecorded(tenantId, userId, method, path, resultCode, durationMs, bodyDigest, occurredAt)`、
  `ErrorLogRecorded(tenantId, userId, exception, message, stackDigest, occurredAt)`
  ——tenantId/userId 在请求线程发布时刻快照（异步监听线程不继承 TTL 上下文）
- 实体：`ApiAccessLogEntity`/`ApiErrorLogEntity`——**全局表**（extends
  AbstractAuditableEntity，tenant_id/user_id 普通冗余列非 @TenantId；平台视角写守护不适用）
- 监听器：`AccessLogListener`/`ErrorLogListener`（`@ApplicationModuleListener` 异步消费
  ——AFTER_COMMIT 投递要求事务内发布，发布方（filter/异常处理器）以 TransactionTemplate
  包裹 publishEvent；全局表写不恢复租户上下文）
- 兼留 Phase 1 操作审计 SPI：`AuditEntry` + `AuditRecorder`（业务操作审计，Phase 3 接入）
- 依赖：platform-data（审计基类 + JPA 全栈）、spring-modulith-events-api（注解）；不依赖业务/starter
- 状态：✅ 已兑现（落库链路由 forge-audit-starter 容器集成测试覆盖）
