# forge-data-starter

数据层 starter：引入即获得 JPA（Hibernate 7）+ 判别式多租户 + 审计 + Flyway（PostgreSQL）+
Modulith 事件发布登记（JDBC）的完整装配。连库应用引本件，非 DB 应用引 forge-spring-boot-starter 即可（零数据库依赖）。

## 自动装配（`ForgeDataAutoConfiguration`）

- `CurrentTenantSupplier` 缺省 Bean：`TenantContextHolderSupplier`（TTL 上下文，应用可用自己的 bean 覆盖）
- `ForgeTenantIdentifierResolver`（@ConditionalOnMissingBean）：ignore/无租户 → 平台租户 `"0"`
- **租户 resolver 注册机制（Boot 4.1.2 + Hibernate 7.4.13 实证）**：Boot 的 HibernateJpaConfiguration
  不自动探测 `CurrentTenantIdentifierResolver` bean（字节码零引用），经
  `HibernatePropertiesCustomizer`（Boot 4 新包 `org.springframework.boot.hibernate.autoconfigure`）
  写入 `hibernate.tenant_identifier_resolver`（常量位于 `MultiTenancySettings`，经 `AvailableSettings` 继承引用）。
  `@TenantId` 实体本身即启用 SessionFactory 判别式多租户——无 resolver 时会话打开即失败
- `AuditorAware<String>`：读 `CurrentUserProvider`（无 bean/无用户 → `"system"`）+ `@EnableJpaAuditing`
  （`DateTimeProvider` → `Instant.now()`）
- `IdGenerator` 缺省 Bean：`UuidV7Generator`
- EPP 低优先级默认值：`spring.modulith.events.jdbc.schema-initialization.enabled=true`

## Boot 4 注意事项

- Flyway 自动配置在 **`spring-boot-flyway`** 模块（Boot 4 拆分），裸 `flyway-core` 不触发迁移——本 starter 已带
- 依赖版本全部由 Boot/Modulith BOM 管（flyway 12.4.0、postgresql 驱动、testcontainers 2.0.5 等）

## 软删除

基类 `AbstractAuditableEntity` 采用 Hibernate 7 原生**类级** `@SoftDelete(columnName = "deleted")`
（见 platform-data README 的机制实证记录）。

## 集成实证（THE SPIKE）

`DataStarterIntegrationTest`（继承 platform-test 的 `AbstractIntegrationTest`，真实 postgres:18-alpine +
redis:8-alpine 容器）：双租户隔离（t-a/t-b 互不可见）、平台视角恒空（tenant "0"）、审计自动填充
（createdAt 非空、无认证上下文 createdBy="system"）、软删除（查询过滤 + DB 行仍在 deleted=true）——全绿。
