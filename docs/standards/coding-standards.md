# 编码规范（v0）

- **不使用 Lombok**：值对象用 record；实体用普通类 + 显式 getter/setter；日志 `private static final Logger log = LoggerFactory.getLogger(X.class)`
- 包根：`io.github.luckybearbear.forge`；platform 模块 `…forge.platform.<x>`；契约 `…forge.contract.<domain>`
- 时间：一律 `Instant`（UTC 存储，ISO-8601 序列化，Jackson 3 默认）
- ID：`IdGenerator`（UUIDv7）字符串
- 控制器显式返回 `Result<T>`（不做自动包装）；错误码实现 `ErrorCode` 接口并登记前缀
- Filter 顺序从 `FilterOrder` 取段，业务 Filter ≥ 0
- 模块内分层：`api`（REST 入参/控制器）/ `app`（服务编排）/ `domain`（领域对象+存储）
- 单测 JUnit 5 + AssertJ；集成测试坚持 Testcontainers（不用 H2/jedis-mock）
