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
必须先恢复具体租户上下文。Phase 2b 将加守护：persist 租户实体时若处于 ignore 则
fail-fast/warn。

## Starter 拆分边界

| Starter | 定位 | 内容 |
|---|---|---|
| forge-spring-boot-starter | **web 基线**，所有应用默认引入 | API 双端前缀（/app-api、/admin-api）+ 斜杠归一、body 重复读、Long→String、全局异常（含乐观锁 409）、OpenAPI |
| forge-data-starter | **按需**：连库应用 | 租户 resolver 注册、JPA 审计（AuditorAware→system 兜底）、IdGenerator bean、Modulith 事件 JDBC 登记默认 |
| forge-security-starter | **按需**：需认证应用 | SecurityFilterChain 默认（stateless/401/403 统一 Result）、TokenStore 装配（Redis 优先、无 Redis 降级内存）、TokenAuthFilter、@TenantIgnore AOP |

约束：**starter 之间禁止互相依赖**（data/security 只依赖 platform 模块）；二者组合由应用自行叠加引入。
