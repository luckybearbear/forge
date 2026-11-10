# forge-platform-web

全局异常处理器 `GlobalExceptionHandler`（借鉴 yudao-cloud 覆盖清单，适配 Boot 4 /
jakarta / Jackson 3：业务异常、校验失败 400+字段 map、参数不可读、405/415 等）+
`FilterOrder` 过滤器顺序常量（CORS→请求体包装→租户→安全→访问日志）+
Phase 2a 三件套：`CacheRequestBodyFilter`（body 可重复读包装，排除 URL 直通）、
`JacksonLongToStringModule`（Long/long → String，防 JS 精度丢失）、
乐观锁冲突 409（`ObjectOptimisticLockingFailureException` → `FORGE-0423`）。

- HTTP 语义保留策略：错误码与 HTTP 状态解耦，HTTP 状态仅传输提示（默认 200，`withHttpStatus` 可覆盖）
- 依赖：platform-core、spring-orm（仅乐观锁异常类型）；由 forge-spring-boot-starter 装配（`@ConditionalOnMissingBean` 可覆盖）
- 状态：✅ Phase 1 完整 + Phase 2a 三件套
