# forge-services

业务共享能力聚合（服务=自动配置库形态：引依赖即得 bean/建表/路由，Phase 2b 起）。

- forge-service-tenant：租户/套餐服务——**范式样板**（模块形态/全局表约定见其 README，其余服务照抄）
- forge-service-identity：身份服务——双端登录/登出/改密 + 租户用户管理 + platform_admin 种子
- forge-service-organization：组织服务——组织树（环检测/删除保护）+ 岗位 + 用户挂岗
- forge-service-authorization：授权服务——角色/权限点/用户角色分配 + 权限快照缓存 + 租户开通初始化
- 依赖规则：services 依赖 platform + contracts；**服务间无 Maven 依赖**（结构性强制），跨服务只走
  contracts 契约 + 领域事件（见 `docs/architecture/dependency-rules.md`）
- 状态：✅ 聚合（四服务 Phase 2b 交付）
