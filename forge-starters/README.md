# forge-starters

starter 聚合 POM：统一自动装配出口，应用只引 starter 不直接拼平台模块。

- 命名规范：`forge-<feature>-starter`（当前含 forge-spring-boot-starter、forge-data-starter、forge-security-starter、forge-cache-starter、forge-audit-starter）
- 约定：data/security/cache 互不依赖；forge-audit-starter 为组合型 starter
  （2b Task 7 裁定）——横切组合 web 过滤器 + 事件登记 + JPA 落库，依赖 web/data starter
- 状态：✅ 聚合
