# forge-spring-boot-starter

底座统一入口。引入即获得：webmvc + validation + actuator（默认仅 health,info）+ 全局异常处理
+ OpenAPI 基线（forge.openapi.title/description/version）+ 日志基线（logback-spring.xml，MDC traceId）
+ 双端前缀路由（controller.app/admin.* → /app-api、/admin-api，`forge.web.api-prefix.*`）
+ Long→String（防 JS 精度丢失，全部 Jackson 消费者生效）
+ body 可重复读（`forge.web.access-log.*`：enabled 默认 true、max-body-size 默认 8KB、
exclude-urls 精确路径排除；访问日志落库消费方 2b 接入）。

`prod` profile 下默认关闭 API 文档（springdoc api-docs / swagger-ui enabled=false，低优先级默认值，应用可显式覆盖开启）。

状态：✅ Phase 2a（web 基线 + 前缀路由 + Long→String + body 缓存装配）。
