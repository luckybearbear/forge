# forge-platform-data

JPA 数据访问基线（Phase 2 spec §3/§6.3 正式选型，替换 Phase 1 的 Spring Data JDBC）：

- `AbstractAuditableEntity`：UUIDv7 主键（`@Id` + `@PrePersist` 自动赋值）、乐观锁（`@Version`）、
  审计四字段（`@CreatedBy/@CreatedDate/@LastModifiedBy/@LastModifiedDate` + `AuditingEntityListener`，
  应用侧需 `@EnableJpaAuditing` + `AuditorAware`）、软删除 `deleted`
  （`@SQLRestriction("deleted = false")` 查询过滤 + `@SQLDelete` 删除置 true）
- `AbstractTenantEntity extends AbstractAuditableEntity`：`@TenantId String tenantId`，
  Hibernate 判别式多租户自动过滤/注入（spec §6.1 入口 2）
- `ForgeTenantIdentifierResolver`：消费 platform-core 的 `CurrentTenantSupplier`（TTL 实现由
  platform-security 提供），`ignore`/无租户 → 平台租户 `"0"`（租户表恒空 = 平台视角，spec §6.1）；
  `validateExistingCurrentSessions()=false`
- Flyway 规范：见 `docs/database/flyway-conventions.md`（目录 `src/main/resources/db/migration`，
  命名 `V{yyyyMMddHHmm}__{snake_case}.sql`）
- 状态：✅（编译 + 反射级验证通过；软删除/`@TenantId` 运行时行为由 Phase 2a Testcontainers
  集成实证——`@SQLDelete` 的 `{h-table}` 占位符若实证不支持，按计划 fallback 降级为
  `@SQLRestriction` 过滤 + service 层显式置位并在此记录差异）
- **不进 forge-spring-boot-starter**：连库模块/data starter 自行装配，保证非 DB 应用零数据库依赖
