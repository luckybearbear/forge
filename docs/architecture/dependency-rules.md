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
- 异步通知：发布方发布契约事件（如 MoldRepairRequested），消费方 @ApplicationModuleListener 监听（目标规范注解；当前 Demo 因 Modulith 无持久化 registry 降级为同步 @EventListener，Phase 2 随 PostgreSQL 恢复）
- 禁止：直接访问其他模块的内部包/内部表
