# 分层依赖规则

| 调用方 | 允许依赖 | 禁止 |
|---|---|---|
| platform | JDK、第三方基础组件 | 任何业务模块 |
| contracts | platform-core（可选） | services / apps 实现 |
| services | platform、contracts | apps 内部实现 |
| apps | platform、services、contracts | 其他 apps 内部实现 |

执行机制（三层）：
1. Maven reactor 结构性强制（apps 互依在 POM 层不可达）
2. Spring Modulith `ApplicationModules.verify()`（应用内边界，见 forge-demo-app ModulithBoundaryTest）
3. 本文档 + Code Review（ArchUnit 评估于 Phase 4）

跨模块协作方式：
- 同步查询：消费方依赖契约模块（如 forge-contract-mold 的 MoldQueryService），实现方在模块暴露面提供 Bean
- 异步通知：发布方发布契约事件（如 MoldRepairRequested），消费方 @ApplicationModuleListener 监听（事务提交后异步投递 + event_publication 持久化登记 + 失败重投；Demo 已连 PostgreSQL 恢复此模式）
- 禁止：直接访问其他模块的内部包/内部表

## 服务=自动配置库（Phase 2b 起形态）

四业务服务（tenant/identity/organization/authorization）以**自动配置库**形态交付——应用只引 jar（经
forge-bom）即得全部 bean、建表与双端路由，无需任何显式装配：

- **登记**：jar 内 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  指向各 `*ServiceAutoConfiguration`
- **扫描**：`@ComponentScan(模块根包)`（显式挂 `AutoConfigurationExcludeFilter` 防与消费方组件扫描
  双路径重复注册）+ `@EntityScan`/`@EnableJpaRepositories` 限 `domain` 子包；无 `@Bean` 方法，组装
  全经组件扫描——覆盖治理点为整模块（不引依赖即整体让位）
- **建表**：jar 内 `db/migration` 随 Flyway 缺省位置自动执行（identity 含 platform_admin 种子行）

**消费方注意（Task 8 组装实证）**：任一显式 `@EnableJpaRepositories` 在场（四服务与
forge-audit-starter 都带）→ Boot 的 `JpaRepositoriesAutoConfiguration` 按
`@ConditionalOnMissingBean` **整体退避**——默认"扫应用根包注册仓储/实体"不再发生，应用自己的
仓储/实体丢失（组装冒烟实证 `NoSuchBeanDefinitionException`）。**消费方须自声明自身扫描范围**
（`@EntityScan` + `@EnableJpaRepositories` 指向应用包；各处声明经 Spring Data 注册器**累加**共存、
互不覆盖。Boot 4 注意 `@EntityScan` 在 `org.springframework.boot.persistence.autoconfigure`）。
参照 `ForgeDemoApplication`。

**跨服务仅契约（结构性强制）**：服务之间**无 Maven 依赖**（services 内互依在 POM 层不可达）——
跨服务协作只走 contracts 契约接口（如 identity 经 `TenantQueryService`/`UserPermissionsQuery`
消费 tenant/authorization 的能力）与下表领域事件。

### 服务领域事件清单（六事件，契约 payload 必带 tenantId）

| 契约事件 | 发布方 | 消费方 |
|---|---|---|
| `TenantCreated` | service-tenant `TenantService`（创建） | service-authorization `TenantCreatedListener`（异步初始化租户 ADMIN 角色与默认权限） |
| `TenantDisabled` | service-tenant `TenantService`（停用） | 暂无（预留：Phase 3 会话失效/通知） |
| `UserCreated` | service-identity `UserService`（创建） | 暂无（预留：通知类扩展） |
| `UserPasswordChanged` | service-identity `UserService`（改密） | forge-security-starter `UserPasswordChangedListener`（**同步** @EventListener → `TokenStore.revokeByUserId`，改密返回时旧 token 已全失效） |
| `OrgUnitChanged` | service-organization `OrgUnitService`（增/改/删） | 暂无（预留） |
| `RolePermissionsChanged` | service-authorization `RoleService`（assignPermissions） | 暂无域外消费（域内权限快照缓存由写路径显式 evict；事件入 event_publication 登记表） |

发布均在业务事务内（`event_publication` 持久化登记 + 提交后异步投递）；唯一例外是
`UserPasswordChanged`（同步吊销，安全优先于事务边界——发布方事务若回滚吊销仍生效，安全方向无害）。
平台侧另有审计事件（`AccessLogRecorded`/`ErrorLogRecorded`，非跨服务契约——发布方为
audit-starter 的 `ApiAccessLogFilter` 与 web-starter 的 `GlobalExceptionHandler`，消费方为
platform-audit 监听器异步落库）；Demo 的 `MoldRepairRequested` 沿用为 2a 契约事件示例。

### Filter 时序约束（FilterOrder 全局段）

| 段 | Filter | 时序约束 |
|---|---|---|
| `REQUEST_BODY_WRAPPER = -150` | web-starter `CacheRequestBodyFilter` | 在 Security 链**前**包装请求体（`ContentCachingRequestWrapper`）；审计 body 摘要须经 `WebUtils.getNativeRequest` 沿 wrapper 链解取——Security 链的 `FirewalledRequest` 会再裹一层，直接 instanceof 判定恒 false（Task 8 全栈实证） |
| `SECURITY_FILTER = -100` | Security 链（`FilterChainProxy`，内含 `TokenAuthFilter`） | 认证成功填充租户/用户上下文，链尾 finally 全清理（见"多租户四入口-1"；链内 filter 不经 registration order 排序） |
| `ACCESS_LOG_FILTER = -80` | audit-starter `ApiAccessLogFilter` | **出口（finally）读租户/用户上下文，依赖 TokenAuthFilter（-100 段）的 finally 清理更晚执行**——访问日志必须注册在安全链之后（order 数值更大）；读上下文的业务自定义 Filter 同理（自 `BUSINESS_FILTER_DEFAULT=0` 起自然满足） |

### 认证链要点

- **TokenGrant 权限快照冻结**：登录时权限集冻结进 `TokenGrant(userId, tenantId, userType,
  authorities)`——授权变更（角色增减权限/用户换角色）后**旧 token 不变**（快照冻结至过期/登出），
  **重新登录取新集**（authorization 侧快照缓存 `auth:perm:{userId}` 由写路径显式 evict）；token 内
  权限刷新属 Phase 3。
- **双端 token**：app 端 grant=(userId, tenantId, MEMBER, 权限集)；admin 端
  grant=(adminId, null, PLATFORM, [PLATFORM])——`/admin-api/**` 链级
  `hasAuthority("PLATFORM")` 门槛（security-starter 默认链：MEMBER token → 403 FORGE-0403；
  `/admin-api/auth/login` 在 permitAll 默认项，不受影响）。
- 两条安全输入（Task 4）：登录**恒时比较**（用户不存在/停用/密码错三路径各恰一次等代价 bcrypt，
  响应时间不可枚举用户名，见 `AuthService#constantTimeVerified`）；**改密吊销**（见事件表
  `UserPasswordChanged` 行）。

### OSIV：建议关闭

判别式租户在 **Hibernate 会话开启时**解析——OSIV（Boot 默认开）的请求级会话在租户上下文填充
**之前**开启，判别式恒解析为平台租户 `"0"`（租户表恒空）；且 Boot 4 + Hibernate 7.4 下以
`REQUIRES_NEW` 另开会话**不足以**恢复（Task 8 全栈实证：appLogin 恒 IDENT-0401，关 OSIV 即恢复）。
连库应用建议显式 `spring.jpa.open-in-view=false`（demo 已关）；Phase 3 评估 data-starter 默认关闭
并系统性裁定 identity 登录上下文管理与 `@TenantIgnore` 的 OSIV 等价性。

## 多租户四入口

上下文承载：`TenantContextHolder`（platform-security，TransmittableThreadLocal）。四个入口统一隔离：

1. **Web**：`TokenAuthFilter` 由 security-starter 的 SecurityFilterChain **链内**
   `addFilterBefore(ExceptionTranslationFilter.class)` 注入（不暴露为 @Bean Filter——避免 Boot
   全局注册二次执行；锚点取 ExceptionTranslationFilter 而非 AuthorizationFilter，401/403 语义
   实证见 ForgeSecurityAutoConfiguration 注释）。认证成功同时填充租户上下文，finally 全清理
   （SecurityContext / CurrentUser / TenantContext）。`FilterOrder.SECURITY_FILTER=-100` 常量
   当前未被引用，仅作全局 Filter 排序段保留（链内 filter 不经 registration order 排序）；
   `FilterOrder.TENANT_FILTER=-120` 段保留给未来 header 直传租户场景，当前无过滤器占用。
2. **DB**：`@TenantId` 实体（继承 `AbstractTenantEntity`）由 Hibernate 判别式多租户自动过滤/注入，
   resolver（`ForgeTenantIdentifierResolver`，data-starter 经 HibernatePropertiesCustomizer 注册）读
   TenantContextHolder。**未标注 `@TenantId` 的实体 = 全局表**（表级逃生口）。ignore 或无租户时
   resolver 返回平台租户 `"0"`——租户表中不存在 `tenant_id='0'` 的行，查询恒空集：
   **`@TenantIgnore` = 平台视角（只见全局表），不是跨租户全量**；跨租户查询须显式 native SQL 并过评审。
3. **Redis**：有租户时 key/cacheName 统一拼 `t:{tenantId}:` 前缀（`TenantCacheKeys`，
   `TenantRedisCacheManager` 与 `CacheFacade`/`RedisCacheFacade` 门面同规则）；
   ignore 或无租户走 `p:` 平台前缀。
4. **事件**：不依赖隐式上下文传播——**跨模块事件 payload 必带 `tenantId` 字段**（契约层面强制），
   异步监听线程不继承 TTL。标准姿势：监听器入口显式 `TenantContextHolder.setTenantId(event.tenantId())`
   → **委托 `REQUIRES_NEW` service 承载业务**（`@ApplicationModuleListener` 自身的 REQUIRES_NEW
   事务会话在方法体之前开启，判别式租户在会话开启时解析——租户必须早于会话设置；`@TenantIgnore`
   切面同理须 `@Order(0)` 先于事务拦截器，见 TenantIgnoreAspect）→ finally
   `TenantContextHolder.clear()`。参照 Demo：`MoldRepairRequestedListener` +
   `MaintenanceService#createFromEvent`（Phase 2a 实证定稿的模式）。

### 平台视角的写语义（终审 Important #5）

`@TenantIgnore`/ignore 是**读视角**约定：其下查询解析为平台租户 `"0"`，租户表恒空 → 恒空集。
但 ignore **不拦截写**——其下 persist/update 租户实体（继承 `AbstractTenantEntity`）时，
Hibernate 按判别式把 `tenant_id='0'` 写入行，产出**所有租户都不可见的错放数据**（仅平台
native SQL 可见/可清理）。因此：ignore 只用于跨租户只读查询与全局表操作；需要写租户数据
必须先恢复具体租户上下文。**写守护已落地（2b 前置加固）**：`AbstractTenantEntity` 的
`@PrePersist` 回调对平台视角 fail-fast 拒绝（守护 supplier 由 forge-data-starter 装配时注入，
未注入时跳过检查——独立单测场景）。

## Starter 拆分边界

| Starter | 定位 | 内容 |
|---|---|---|
| forge-spring-boot-starter | **web 基线**，所有应用默认引入 | API 双端前缀（/app-api、/admin-api）+ 斜杠归一、body 重复读、Long→String、全局异常（含乐观锁 409、500 兜底发布 ErrorLogRecorded）、OpenAPI |
| forge-data-starter | **按需**：连库应用 | 租户 resolver 注册、JPA 审计（AuditorAware→system 兜底）、IdGenerator bean、Modulith 事件 JDBC 登记默认、CurrentTenantSupplier 装配 |
| forge-security-starter | **按需**：需认证应用 | SecurityFilterChain 默认（stateless/401/403 统一 Result、/admin-api/** PLATFORM 门槛）、TokenStore 装配（Redis 优先、类路径缺席降级内存）、TokenAuthFilter、@EnableMethodSecurity、@TenantIgnore AOP、改密吊销监听 |
| forge-cache-starter | **按需**：Redis 缓存应用 | RedisTemplate（String key/JSON value）基线 + 租户前缀 CacheManager/CacheFacade（@EnableCaching）；CurrentTenantSupplier 生产装配由 data-starter 提供，仅引本 starter 须自备 |
| forge-audit-starter | **按需**：审计落库应用 | **组合型 starter**（Task 7 裁定）：ApiAccessLogFilter（-80）+ platform-audit 监听器（@ApplicationModuleListener 异步落 api_access_log/api_error_log）+ audit domain 的 EntityScan/RepoScan 与 jar 内建表迁移；依赖 web/data starter |

约束（Task 7 修订）：data/security/cache **互不依赖**（只依赖 platform 模块），组合由应用自行叠加引入；
例外是 **forge-audit-starter 为组合型 starter**——审计=web 过滤器+事件登记+JPA 落库的横切组合，
依赖 forge-spring-boot-starter 与 forge-data-starter；platform 模块仍不依赖任何 starter。
