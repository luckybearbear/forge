# 编码规范（v0）

- **不使用 Lombok**：值对象用 record；实体用普通类 + 显式 getter/setter；日志 `private static final Logger log = LoggerFactory.getLogger(X.class)`
- 包根：`io.github.luckybearbear.forge`；platform 模块 `…forge.platform.<x>`；契约 `…forge.contract.<domain>`
- 时间：一律 `Instant`（UTC 存储，ISO-8601 序列化，Jackson 3 默认）
- ID：`IdGenerator`（UUIDv7）字符串
- 控制器显式返回 `Result<T>`（不做自动包装）；错误码实现 `ErrorCode` 接口并登记前缀
- **JPA 实体必须继承基类**：全局表 extends `AbstractAuditableEntity`，租户表 extends
  `AbstractTenantEntity`（其 `@TenantId` 由 Hibernate 自动过滤/注入）——UUIDv7 主键、乐观锁、
  审计四列、软删除均由基类机制承载，实体内不重复声明
- **软删除走类级 `@SoftDelete`**（基类已标注，`deleted` 列由 Hibernate 独占管理）：remove 自动改写
  为 UPDATE、查询自动过滤；**无 `deleted` 字段/访问器 API**，恢复已删数据需另行设计（当前未提供）
- **异步监听器消费租户事件的标准姿势**：事件 payload 必带 `tenantId`；监听器入口显式
  `TenantContextHolder.setTenantId` → 委托 `REQUIRES_NEW` service 承载业务 → finally
  `clear`（会话先于方法体开启，租户须早于会话设置；详见 docs/architecture/dependency-rules.md 多租户四入口）
- Filter 顺序从 `FilterOrder` 取段，业务 Filter ≥ 0
- 模块内分层：`api`（REST 入参/控制器）/ `app`（服务编排）/ `domain`（领域对象+存储）
- 单测 JUnit 5 + AssertJ；集成测试坚持 Testcontainers（不用 H2/jedis-mock）
