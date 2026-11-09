# forge-distributions

应用组装层聚合 POM：不同 SaaS 产品在此组装不同模块组合（platform + 契约 + 业务模块 + starter）。

- 当前含 forge-demo-app（mold + maintenance 双模块可运行 Demo）
- 约定：Boot 插件在根 pom 默认 `<skip>true</skip>`（CLI `spring-boot:run` 不作用于库模块）；distributions 下的可运行应用须在自身插件声明中覆盖 `<skip>false</skip>`（见 forge-demo-app）
- 状态：✅ 聚合
