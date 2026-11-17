# forge-platform-test

测试工具基座。

- `ResultAssertions`：assertOk/assertError 等 Result 断言
- `AbstractIntegrationTest`：`@Testcontainers(disabledWithoutDocker = true)` 基类——静态
  `postgres:18-alpine`（`@ServiceConnection`）+ `redis:8-alpine`（统一基座，data-starter 测试未消费，
  供 security-starter/demo 复用；不带 `@ServiceConnection`——Boot 4 的 Redis ConnectionDetails
  工厂随 spring-boot-redis 模块分布，无该模块的测试挂上会 ConnectionDetailsNotFoundException）
- `TenantTestSupport`：`runAs`/`runAsResult`（set/finally clear）+ `runAsPlatform`（ignore=true 平台视角）
- 依赖：platform-core、platform-security（TenantContextHolder）、spring-boot-testcontainers、
  Testcontainers 2.x 改名件（`testcontainers-junit-jupiter`/`testcontainers-postgresql`，Boot BOM 管 2.0.5；
  类包名与 1.x 相同 `org.testcontainers.*`，jar 内另有 `org.testcontainers.postgresql` 新包别名）——
  均为 compile 供下游 test 引用传递，同 junit/assertj 约定
- 状态：✅（Task 9 起承载 Testcontainers 容器基座，Phase 2 不采纳 H2/jedis-mock，避免与生产行为漂移）
