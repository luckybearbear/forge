# forge-platform-data

数据访问基线（Spring Data JDBC）。Phase 1 半成品：

- `AbstractAuditableEntity`：ID（String/UUIDv7）+ 乐观锁 `@Version` + 审计字段
- Flyway 规范：见 `docs/database/flyway-conventions.md`（目录 `src/main/resources/db/migration`，命名 `V{yyyyMMddHHmm}__{snake_case}.sql`）
- 状态：⚙️ 半成品（有意）；Phase 2 随 PostgreSQL + Testcontainers 实装 `@EnableJdbcAuditing`、AuditorAware、多租户列策略
- **不进 forge-spring-boot-starter**：连库模块自行引入，保证非 DB 应用零数据库依赖
