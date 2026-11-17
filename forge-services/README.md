# forge-services

业务共享能力聚合（服务=自动配置库形态：引依赖即得 bean/建表/路由，Phase 2b 起）。

- forge-service-tenant：租户/套餐服务——**范式样板**（模块形态/全局表约定见其 README，identity/organization/authorization 照抄）
- 依赖规则：services 依赖 platform + contracts；跨模块调用只走 contracts + 事件（见 `docs/architecture/dependency-rules.md`）
- 状态：✅ 聚合（tenant 已落，其余 Phase 2b 排期）
