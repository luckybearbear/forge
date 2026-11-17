# forge-audit-starter

审计自动装配（spec §6.6，Phase 2b）：API 访问/错误日志落库的组装出口。应用引本 starter 即得
filter → Modulith 事件（event_publication 先登记后投递）→ JPA 异步落库全链。

- 访问侧：`ApiAccessLogFilter`（`FilterOrder.ACCESS_LOG_FILTER=-80`，web-starter 的
  CacheRequestBodyFilter 装配 body 缓存前置）——入口计时，链后（finally）快照
  TenantContextHolder/ThreadLocalUserProvider + `ContentCachingRequestWrapper` 缓存体
  （截断 ≤ `forge.web.access-log.max-body-size`，缺省 8KB，剥离 ISO 控制字符——PG text
  拒绝 U+0000）发布 `AccessLogRecorded`（publishEvent 以 TransactionTemplate 包裹——
  @ApplicationModuleListener 为 AFTER_COMMIT 投递，无事务发布直接丢弃；发布点自身无自然
  事务；事件 compact constructor 按列长截断 tenant/user/method/path/exception——超长值
  会使落库 INSERT 失败、event_publication 卡 incomplete）
- 错误侧：发布点在 `GlobalExceptionHandler`（web-starter 装配，500 兜底分支发布
  `ErrorLogRecorded`，同样经 TransactionTemplate 包裹；**业务/4xx 分支不发**——常态流量
  不算错误，取舍见类 javadoc；无本 starter 时发布为无害空投）
- 落库：platform-audit 的 `AccessLogListener`/`ErrorLogListener`
  （`@ApplicationModuleListener` 异步消费；全局表写不恢复租户上下文，payload 显式携带）→
  `api_access_log`/`api_error_log`（全局表：tenant_id/user_id 普通冗余列，非判别式）
- 迁移：jar 内 `db/migration`（Flyway 缺省位置自动拾取），表/索引见附录 A
- 开关：`forge.web.access-log.enabled=false`（缺省 true，与 body 缓存过滤器同门成对关闭）
- 覆盖治理：应用自带 `ApiAccessLogFilter` 时让位（`@ConditionalOnMissingBean`）

依赖（组合型 starter，Task 7 裁定——修正 starter 互不依赖约定；platform-audit 仍不依赖任何
starter，"platform 不依赖 starter"铁律不破）：platform-audit（事件/实体/监听器）+
forge-data-starter（Modulith JDBC 登记表/JPA/Flyway）+ forge-spring-boot-starter（web 栈与
`forge.web.access-log.*` 属性）+ platform-security（CurrentUser/Tenant 上下文读取）。

- 状态：✅ 装配完成（容器集成测试：HTTP 请求 → 行断言 + event_publication COMPLETED）
