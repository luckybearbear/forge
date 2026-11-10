# forge-platform

技术基础层聚合 POM：core（统一响应/错误码/异常/UUIDv7）、web（全局异常处理/FilterOrder）、
security（用户/租户上下文抽象）、data（JPA 审计基线）、test（测试工具）、
observability（MDC 常量/日志规范）+ cache/messaging/audit/storage 四个 SPI 占位。

- 只承载技术能力，禁止承载任何业务概念（依赖规则见 `docs/architecture/dependency-rules.md`）
- 状态：✅ 聚合（子模块状态各异，见各自 README）
