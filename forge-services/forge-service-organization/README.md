# forge-service-organization

组织服务：组织树（环检测/删除保护）+ 岗位 + 用户挂岗——服务=自动配置库形态（照抄
`forge-service-tenant` 范式样板，形态要点见该模块 README）。

## 模块形态

- **pom**：parent `forge-services`；依赖 `forge-data-starter` + `forge-spring-boot-starter` +
  `forge-contract-organization` + `forge-contract-identity`（platform-core 显式声明）；
  test：`spring-boot-starter-test` + `forge-platform-test`。无 Lombok，**不依赖任何 service**。
- **自动配置**：`OrganizationServiceAutoConfiguration` = `@AutoConfiguration` +
  `@ComponentScan(模块根包)` + `@EntityScan(domain)` + `@EnableJpaRepositories(domain)`（Boot 4
  注意：`@EntityScan` 在 `org.springframework.boot.persistence.autoconfigure`）；无 `@Bean` 方法。
- **装配前提**：`UserPositionService` 注入契约 `UserQueryService`（实现方为 service-identity）——
  引本 jar 的应用须同时具备该 bean（引 service-identity 即得；否则启动失败，装配期可见）。
- **包布局**：`domain/`（实体+仓库，租户表）· `app/`（应用服务 + 契约实现 `OrgTreeQueryServiceImpl`）·
  `controller/app/`（应用端 REST + 请求 DTO record）· 模块根包（自动配置）。
- **Flyway**：`V202611181200__create_org_tables.sql`（org_unit/org_position/user_position）随 jar 走。
- **路由**：控制器在 `controller.app.*` 包 → 双端前缀自动前插 `/app-api`。

## 域语义

- **三表均租户表**（extends `AbstractTenantEntity`）：判别式过滤/自动注入，org 数据天然租户隔离。
  树形无物理 FK/无 path 物化：`parent_id` 为 null 即根（每租户可多根=森林）。
- **环检测（move）**：从新父沿 `parentId` 逐级上溯，遇被移动单元自身（含 newParent=自己）即环 →
  `ORG-0409`/409 回滚；上溯超 **64 层**同样拒绝（脏数据防线——库里若已有环，上溯永不终止，
  上限熔断防死循环；上溯链断裂按 `ORG-0404` 诚实暴露）。合法 move 发 `OrgUnitChanged(update)`。
- **update 部分更新**：type/name/sort 传 null 不改；**父变更不在 update 范围**（走 move）。
- **删除保护**：组织单元有子节点或挂靠岗位 → `ORG-0409` 码 + 文案"存在子节点或挂岗，无法删除"
  （FOREIGN_CODES 纪律：不新增枚举）；岗位有挂岗 → 同码 + "岗位存在挂岗，无法删除"。
  删除均为软删（编码/名字可复用由 partial unique index 承载）。
- **挂岗**：userId 存在性经 contract-identity `UserQueryService`（缺失 → `IDENT-0404`/404）；
  positionId 本域校验（`ORG-0414`/404）。**幂等挂岗**：同 (tenant, user, position) 再挂返回既有
  记录（先查后插 + partial unique index `WHERE deleted = FALSE` 兜底并发）；**幂等摘岗**：不存在
  静默成功；摘后可重挂（软删行不占键）。
- **事件**（业务事务内，spec §10）：`OrgUnitChanged`（changeType 三态字面量 `create`/`update`/`delete`；
  update 含 move）。tenantId 为**事件主体租户**——create 侧 @TenantId 插入时才绑定，publish 时取
  `CurrentTenantSupplier`（platform-core 接口，data-starter 装 TTL 实现）；加载态实体直接取实体字段。
- **树查询**：`tree()` 全林嵌套（兄弟 (sort, id) 稳定排序）；`subTree`/契约 `OrgTreeQueryService.findSubTree`
  返回根开头先序扁平快照——**根缺失/他租户返回空集**（快照语义：消费方据此失效缓存，不区分 404）。
  树形递归自根而下，环上节点不可达自根——脏环不会令查询悬挂。

## 错误码（FOREIGN_CODES 纪律：本模块零新增）

- `ORG-0404`（单元不存在，404）、`ORG-0409`（环/删除保护/深度上限，409——消息按场景区分）、
  `ORG-0414`（岗位不存在，404）、`IDENT-0404`（用户不存在，404——契约跨域）。

## 接口（实际路径含 /app-api 前缀）

- `POST /org-units`、`GET /org-units/{id}`、`GET /org-units/tree`、`GET /org-units/{id}/sub-tree`、
  `PUT /org-units/{id}`、`PUT /org-units/{id}/move`、`DELETE /org-units/{id}`
- `POST /positions`、`GET /positions/{id}`、`GET /positions?orgUnitId=`、`PUT /positions/{id}`、
  `DELETE /positions/{id}`
- `POST /user-positions`（挂岗）、`GET /user-positions?userId=`、`DELETE /user-positions?userId=&positionId=`（摘岗）
- 契约查询 bean：`OrgTreeQueryService`（findSubTree → `List<OrgUnitDTO>`，跨模块同步消费）

## 集成实证

`OrganizationServiceIntegrationTest`（真实 PG 容器；测试类在服务根包外的独立分支包
`io.github...forge.organization`，`@Bean` 桩供 `UserQueryService`——Task 4 防交叉泄漏范式）：
树 CRUD/树形/子树（服务+契约双路）、**环检测必红路径**（A 移到子孙 C 下 ORG-0409 且树形不变、
移到自身、脏数据环 64 层熔断）、删除保护（子节点/岗位/挂岗三层）、岗位 CRUD（含换挂靠）、
挂岗契约校验（IDENT-0404/ORG-0414）、幂等挂岗/摘岗/重挂、双租户隔离（三表互不可见 + 事件租户
打标）——6/6 全绿。控制器薄层（校验+Result 包装）；RANDOM_PORT 全链路由 demo 装配（Task 8）承载。
