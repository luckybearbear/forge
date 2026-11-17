# forge-platform-data

JPA 数据访问基线（Phase 2 spec §3/§6.3 正式选型，替换 Phase 1 的 Spring Data JDBC）：

- `AbstractAuditableEntity`：UUIDv7 主键（`@Id` + `@PrePersist` 自动赋值）、乐观锁（`@Version`）、
  审计四字段（`@CreatedBy/@CreatedDate/@LastModifiedBy/@LastModifiedDate` + `AuditingEntityListener`，
  应用侧需 `@EnableJpaAuditing` + `AuditorAware`）、软删除 `deleted`
  （**最终机制：Hibernate 7 原生类级 `@SoftDelete(columnName = "deleted")`，经基类被子类继承**——
  remove 自动改写为 `UPDATE ... SET deleted = true`，所有读取/查询自动过滤 `deleted = false`；
  deleted 列由 Hibernate 独占管理、不映射为实体属性。Task 9 spike 实证原方案不可用：
  `@SQLDelete` 在 `@MappedSuperclass` 上不被 Hibernate 读取（`EntityBinder.bindCustomSql` 不遍历基类，
  实际行为为物理 DELETE 的假绿），且 `{h-table}` 占位符在 hibernate-core 7.4.13 全 jar 0 命中不存在；
  字段级 `@SoftDelete` 在 7.4.13 绑定侧无处理（仅类级/集合级），故采用类级；`@SQLRestriction`
  虽经基类遍历可生效，但过滤语义已被 @SoftDelete 一体覆盖，一并移除）
- `AbstractTenantEntity extends AbstractAuditableEntity`：`@TenantId String tenantId`，
  Hibernate 判别式多租户自动过滤/注入（spec §6.1 入口 2）
- `ForgeTenantIdentifierResolver`：消费 platform-core 的 `CurrentTenantSupplier`（TTL 实现由
  platform-security 提供），`ignore`/无租户 → 平台租户 `"0"`（租户表恒空 = 平台视角，spec §6.1）；
  `validateExistingCurrentSessions()=false`
- Flyway 规范：见 `docs/database/flyway-conventions.md`（目录 `src/main/resources/db/migration`，
  命名 `V{yyyyMMddHHmm}__{snake_case}.sql`）
- 状态：✅（Task 9 Testcontainers 集成实证通过：双租户隔离/平台视角恒空/审计填充/软删除
  ——`forge-data-starter` 的 `DataStarterIntegrationTest` 真实 PG 上全绿）
- **不进 forge-spring-boot-starter**：连库模块/data starter 自行装配，保证非 DB 应用零数据库依赖
