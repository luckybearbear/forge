# forge-platform-test

测试工具基座：`ResultAssertions`（assertOk/assertError 等 Result 断言）。

- Phase 2 增 Testcontainers 真实 PostgreSQL 基类（不采纳 H2/jedis-mock，避免与生产行为漂移）
- 依赖：platform-core（test scope 供下游使用）
- 状态：✅ 基础（Phase 1 范围完整）
