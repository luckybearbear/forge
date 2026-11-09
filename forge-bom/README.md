# forge-bom

依赖版本唯一入口：import `spring-boot-dependencies` 4.1.2 / `spring-modulith-bom` 2.1.2，管理全部 forge 构件版本 + `springdoc-openapi-starter-webmvc-ui` 3.2.0。外部消费者只需 import 本 BOM，无需写版本号。

- parentless（对齐 Spring 官方 `spring-boot-dependencies` 模式，避免与根 pom 互相 import 成环）；`revision` 等版本属性与根 pom 两处同步，升版本需同改
- 依赖：无（纯 BOM，packaging=pom）
- 状态：✅ Phase 1 完整
