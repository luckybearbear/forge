# forge-service-authorization

授权服务：**角色 / 权限点（含菜单合一字典树）/ 用户角色分配 / 数据权限（dataScope） + 权限快照缓存
+ 租户开通初始化 + 可见菜单查询**（服务=自动配置库形态，照抄 `forge-service-tenant` 范式样板）。

## 模块形态（同 service-tenant 样板）

- **pom**：parent `forge-services`；依赖 `forge-data-starter` + `forge-spring-boot-starter` +
  **`forge-security-starter`**（@EnableMethodSecurity 激活 @PreAuthorize + TenantContextHolder）+
  **`forge-cache-starter`**（CacheFacade 装配——权限快照缓存的装配点，Task 6 先行交付）+
  `forge-contract-authorization` + `forge-contract-tenant`（TenantCreated 监听）+
  `forge-platform-core`/`forge-platform-cache`/`forge-platform-security`（主代码直接使用，显式声明）。
  **不依赖任何 service**；也**不消费 contract-identity**——权限不随密码变（无 UserPasswordChanged 联动，
  改密吊销 token 在 forge-security-starter 侧承接，见其 README）。test 另有 awaitility
  （@ApplicationModuleListener 异步消费断言）。无 Lombok。
- **自动配置**：`AuthorizationServiceAutoConfiguration` = `@AutoConfiguration` + `@ComponentScan(模块根包)`
  + 显式 `AutoConfigurationExcludeFilter` 自排 + `@EntityScan(domain)`（Boot 4 新包）+
  `@EnableJpaRepositories(domain)`；无 `@Bean`。**@EnableMethodSecurity 不在此**（Task 4 裁定归属
  forge-security-starter）。双路径装配（imports 文件 + 消费方 @Import）均已集成实证。
- **包布局**：`domain/`（实体+仓库+枚举）· `app/`（RoleService/UserRoleService/PermissionService/
  UserPermissionsQueryImpl/TenantInitializationService/TenantCreatedListener + DTO）· 模块根包
  （自动配置 + 契约实现 `MenuQueryServiceImpl`）· `controller/app/`、`controller/admin/`
  （双端前缀路由）。
- **Flyway**：`V202611181300__create_auth_tables.sql` 随 jar 走——四表 + partial unique indexes +
  **权限点字典 seed**（固定 id）：user:manage/role:manage/org:manage + mold:create/mold:read/
  mold:repair/maint:read（均 API 型）；`V202611190000__alter_permission_menu.sql`（菜单切片）——
  auth_permission +parent_id/path/component/icon/sort/visible 六列 + **菜单树 seed**（dashboard/
  sys-dir/tenant:manage；role:manage 经 UPDATE 转 MENU 挂 sys-dir，component 与 Task 4 页面文件
  一一对应）。

## 表结构

| 表 | 形态 | 唯一性（partial, WHERE deleted = FALSE） |
|---|---|---|
| `auth_role` | 租户表 | (tenant_id, code) |
| `auth_permission` | **全局表**（含菜单树+路由元数据） | code（字典全租户共享——平台级领域语言） |
| `auth_role_permission` | 租户表 | (tenant_id, role_id, permission_id) |
| `auth_user_role` | 租户表 | (tenant_id, user_id, role_id) |

roleId/permissionId/userId 均为**逻辑引用**（无物理 FK：跨域不耦合，软删/字典清理侧重绑）。
permission 软删后的悬挂授权在查询侧自然失效（`findAuthorityCodes` 经字典存活行 join）。

### 菜单合一模型（auth_permission 即菜单字典）

- 菜单=带路由元数据的权限点：`parent_id` 组树、`path/component/icon/sort/visible` 路由元数据、
  `type` 四值 **DIR/MENU/BUTTON/API**；MENU 的 code 即权限码（鉴权与可见性同源）；DIR 不参与
  鉴权（可见性由后代决定）；`visible` 是前端展示开关（后端不据此过滤）。
- **可见菜单树**（`visibleMenus`，契约 `MenuQueryService` 供 identity `/menus` 消费）：MENU 按
  code∈authorities 过滤（`PLATFORM` 超级全通过）→ DIR 祖先链自底向上补全（空 DIR 折叠）→
  children 按 sort 升序（null 最后，同序按 code 定序）。BUTTON/API 不进可见树。
- **admin 字典树**（`tree()`）：全量含 BUTTON/API 与空 DIR（字典管理须见全部行）。
- **约定（service 不强校验，记录在此）**：type=DIR 时路由字段（path/component）可不填——渲染层
  约定；create 的 visible 缺省 TRUE（对齐列 DEFAULT TRUE）；update 部分更新 null 不改，
  **parentId 传空串=挂根**；parentId 环数据由可见性算法防御（上溯 walked 集），字典侧不阻止
  写入（无引用校验，同 delete 悬挂引用语义）。

## 权限快照缓存（UserPermissionsQueryImpl）

- key：`auth:perm:{userId}` → CacheFacade 落为 **`t:{tenantId}:auth:perm:{userId}`**，TTL **5min**；
  值载体 `List<String>`（Jackson default-typing 往返最稳），命中侧归一为不可变 `Set<String>`。
- **程序式**（非 @Cacheable）：写路径显式失效——`RoleService.assignPermissions`（按持有角色的
  userId 集合 evict + 发布 RolePermissionsChanged）、`RoleService.delete`（级联清理前收集持有者、
  删后逐一 evict——评审 Important-1）与 `UserRoleService.assignToUser`（按该 userId evict）→
  变更**即时生效**（不等 TTL）；TTL 仅兜底（如 permission 字典删除的悬挂快照）。evict 均为
  「业务事务内、DB 写后逐出」——并发读在提交与逐出之间回填陈旧快照的经典 cache-aside 竞态由
  TTL 5min 有界兜底（双删/版本化 key 属后续演进，javadoc 注记）。
- **租户上下文与事务（identity 登录范式）**：登录链调用时线程无租户上下文——按参数 tenantId 显式
  set（保存/还原进入前状态，finally）；DB 读走 REQUIRES_NEW 只读事务（新会话保证 @TenantId 判别式
  在 set 之后解析——OSIV 消费应用下 REQUIRED 会挂请求级会话恒查空，同 AuthService 语义）。

## TenantCreated → 默认角色初始化

`TenantCreatedListener`（`@ApplicationModuleListener`：提交后异步 + event_publication 登记 + 重投）
显式 setTenantId(payload.tenantId()) → finally clear（demo 范式；auth_role 为租户表，无上下文即平台
租户 '0' 黑洞）；`TenantInitializationService.initializeDefaultAuthorization` 走 **REQUIRES_NEW**
幂等落库：默认 **ADMIN** 角色（code=ADMIN，name=管理员，dataScope=ALL）+ 字典**全量权限点**授权
（findByCode + 差集跳过——重投/回放不重复）。首个用户绑定经 `PUT /app-api/users/{userId}/roles`
或 demo seeder（简裁定：TenantCreated 不携带 userId，监听器不绑创建人）。

## 端点

- `/app-api/roles`（类级 `@PreAuthorize("hasAuthority('role:manage')")`）：POST/GET/GET {id}/PUT {id}
  /DELETE {id} + **PUT {id}/permissions**（全量替换授权，空集=回收）+ **PUT {id}/data-scope**
- `/app-api/users/{userId}/roles`（同上门禁——角色分配属授权管理面）：PUT（全量替换，空集=回收）/GET
- `/admin-api/permissions`（无 @PreAuthorize——平台 token grant 恒空权限集，门禁=authenticated）：
  POST/GET/GET {id}/PUT {id}（code 不可改）/DELETE {id}。**GET 列表默认树形**（菜单切片 Task 1）：
  返回 `tree()` 全量字典树（含 BUTTON/API 与空 DIR，节点=契约 `MenuNodeDTO`）；create/update 请求
  携带六列菜单元数据（均可空——DIR 路由字段可不填，约定见上节）。
- 可见菜单树不在本面：租户侧 `GET /app-api/auth/menus`（identity 经契约 `MenuQueryService` +
  ObjectProvider 消费，token 快照 authorities 直取）。

## 错误码（FOREIGN_CODES：未加新码）

- AUTH-0404 角色不存在（404）/ AUTH-0414 权限点不存在（404）/ AUTH-0409 角色编码已存在（409）
- **AUTH-0409 复用**：permission 编码冲突同码（409），文案经 `BusinessException(errorCode, message)`
  覆写为"权限点编码已存在: {code}"（enum defaultMessage 为角色文案，不复制的代价，此处登记）。

## 集成实证

`AuthorizationServiceIntegrationTest`（真实 PG+Redis 容器，独立分支包 `...forge.authorization.flow`）：
角色 CRUD/编码租户内唯一/跨租户同码/软删复用/dataScope DB 往返；字典 seed+CRUD+错误码；**缓存三段
证据**（①原始键落盘 ②直接删库行后 TTL 内仍读快照=命中不触库 ③assignPermissions/换绑角色后即时反映）
+ 事件 RolePermissionsChanged；TenantCreated 异步建角（两租户互不可见 + 重投幂等）；同 userId 双租户
快照物理隔离（`t:` 前缀原始键共存、单侧失效不串味）；契约 bean 暴露——全绿。

`MenuVisibilityIntegrationTest`（独立分支包 `...forge.authorization.menu`，菜单切片 Task 1）：
visibleMenus **五语义**（authorities 过滤/PLATFORM 超级全量/空 DIR 折叠/祖先 DIR 链补全/sort 升序
null 最后）+ admin 字典树全量含 BUTTON/API + 六新列读写往返 + 契约 `MenuQueryService` bean 暴露——
全绿。
