# forge-spring-boot-starter

底座统一入口。引入即获得：webmvc + validation + actuator（默认仅 health,info）+ 全局异常处理
+ OpenAPI 基线（forge.openapi.title/description/version）+ 日志基线（logback-spring.xml，MDC traceId）。

`prod` profile 下默认关闭 API 文档（springdoc api-docs / swagger-ui enabled=false，低优先级默认值，应用可显式覆盖开启）。

状态：✅ Phase 1 完整；后续 starter 命名规范 forge-<feature>-starter。
